"""Export a deliberately incomplete, reviewed CC0 Wikidata catalog and country packs.

Run --self-test offline. Normal runs fetch batched entity metadata conservatively,
validate every curated identity/coordinate, then atomically replace the asset.
--delta PATH emits only changed/new records; --remove QID is an explicit deletion.
Missing entities, rate limits and failed requests never imply deletions.
Reviewed manifests bound the request set; --entity-cache saves official snapshots
for --offline reproduction. Existing records survive partial regional imports.
Structured data licensing: https://www.wikidata.org/wiki/Wikidata:Licensing
Categories/significance are editorial, not Wikidata or independent verification.
"""
from __future__ import annotations

import argparse
import copy
from datetime import datetime, timezone
from email.utils import parsedate_to_datetime
import json
import math
import os
from pathlib import Path
import re
import tempfile
import time
import unicodedata
import urllib.error
import urllib.parse
import urllib.request


API = "https://www.wikidata.org/w/api.php"
LANGUAGES = "en|hy|fr|es|ja"
CATEGORIES = {"Historic", "Museum", "Monument", "Building", "Park", "Natural", "Cultural"}
# Exact English label guards prevent a plausible-looking coordinate on a wrong QID.
CURATED = {
    "Q243": ("Eiffel Tower", "Building"),
    "Q9202": ("Statue of Liberty", "Monument"),
    "Q9141": ("Taj Mahal", "Historic"),
    "Q10285": ("Colosseum", "Historic"),
    "Q45178": ("Sydney Opera House", "Cultural"),
    "Q37200": ("Great Pyramid of Giza", "Historic"),
    "Q676203": ("Machu Picchu", "Historic"),
    "Q79961": ("Christ the Redeemer", "Monument"),
    "Q43473": ("Angkor Wat", "Historic"),
    "Q5788": ("Petra", "Historic"),
    "Q5859": ("Chichen Itza", "Historic"),
    "Q131013": ("Acropolis of Athens", "Historic"),
    "Q48435": ("Basilica and Expiatory Church of the Holy Family", "Building"),
    "Q41225": ("Big Ben", "Building"),
    "Q19675": ("Louvre Museum", "Museum"),
    "Q6373": ("British Museum", "Museum"),
    "Q160112": ("Museo del Prado", "Museum"),
    "Q351": ("Yellowstone National Park", "Park"),
    "Q7296": ("Mount Kilimanjaro", "Natural"),
    "Q36332": ("Iguaçu Falls", "Natural"),
    "Q7343": ("Great Barrier Reef", "Natural"),
    "Q33910": ("Uluru", "Natural"),
    "Q39231": ("Mount Fuji", "Natural"),
    "Q12495": ("Burj Khalifa", "Building"),
    "Q12506": ("Hagia Sophia", "Historic"),
    "Q39671": ("Stonehenge", "Historic"),
    "Q80290": ("Forbidden City", "Historic"),
    "Q12501": ("Great Wall of China", "Historic"),
    "Q513": ("Mount Everest", "Natural"),
    "Q82425": ("Brandenburg Gate", "Monument"),
    "Q11812902": ("Serengeti National Park", "Park"),
    "Q213360": ("Table Mountain", "Natural"),
    "Q43278": ("Victoria Falls", "Natural"),
    "Q464535": ("Shwedagon Pagoda", "Cultural"),
    "Q4152": ("Neuschwanstein Castle", "Historic"),
    "Q134883": ("CN Tower", "Building"),
    "Q188754": ("Himeji Castle", "Historic"),
    "Q41116": ("Red Square", "Cultural"),
    "Q129072": ("Persepolis", "Historic"),
    "Q118841": ("Grand Canyon", "Natural"),
}


def clean(value: object) -> str:
    return re.sub(r"\s+", " ", str(value)).strip()[:300]


def identity(value: str) -> str:
    return "".join(c for c in unicodedata.normalize("NFKD", value).casefold() if c.isalnum())


def claims(entity: dict, property_id: str) -> list:
    statements = entity.get("claims", {}).get(property_id, [])
    valid = [s for s in statements if s.get("rank") != "deprecated" and s.get("mainsnak", {}).get("snaktype") == "value"]
    if property_id in {'P17', 'P131'}:
        valid = [s for s in valid if not s.get('qualifiers', {}).get('P582')]
    preferred = [s for s in valid if s.get("rank") == "preferred"]
    return [(s.get("mainsnak", {}).get("datavalue", {}).get("value")) for s in (preferred or valid)]


def linked_ids(entity: dict, property_id: str) -> list[str]:
    return sorted({v["id"] for v in claims(entity, property_id) if isinstance(v, dict) and re.fullmatch(r"Q[1-9][0-9]*", v.get("id", ""))})


def coordinate(entity: dict) -> tuple[float, float]:
    for v in claims(entity, "P625"):
        if not isinstance(v, dict) or v.get("globe") not in {"http://www.wikidata.org/entity/Q2", "https://www.wikidata.org/entity/Q2"}:
            continue
        lat, lon = v.get("latitude"), v.get("longitude")
        if isinstance(lat, (int, float)) and not isinstance(lat, bool) and isinstance(lon, (int, float)) and not isinstance(lon, bool):
            if math.isfinite(lat) and math.isfinite(lon) and -90 <= lat <= 90 and -180 <= lon <= 180:
                return float(lat), float(lon)
    raise ValueError(f"No nondeprecated Earth P625 coordinate: {entity.get('id')}")


def normalize(qid: str, entity: dict, linked: dict, selection: dict = CURATED) -> dict:
    expected, category = selection[qid]
    if entity.get("id") != qid or "missing" in entity:
        raise ValueError(f"Missing/wrong entity: {qid}; existing catalog preserved")
    labels = entity.get("labels", {})
    name = clean(labels.get("en", {}).get("value", ""))
    if not name or identity(name) != identity(expected):
        raise ValueError(f"Curated identity changed: {qid}: {name!r}, expected {expected!r}; review manually")
    lat, lon = coordinate(entity)
    record = dict(id=f"wikidata:{qid}", name=name, lat=lat, lon=lon, category=category,
                  names={lang: clean(v["value"]) for lang, v in labels.items() if v.get("value")},
                  source=f"https://www.wikidata.org/wiki/{qid}")
    for property_id, field in [("P17", "country"), ("P131", "locality")]:
        names = [clean(linked.get(k, {}).get("labels", {}).get("en", {}).get("value", "")) for k in linked_ids(entity, property_id)]
        names = list(dict.fromkeys(n for n in names if n))
        if names:
            record[field] = " / ".join(names)
    if isinstance(entity.get("lastrevid"), int) and entity["lastrevid"] > 0:
        record["revision"] = entity["lastrevid"]
    if entity.get("modified"):
        datetime.fromisoformat(entity["modified"].replace("Z", "+00:00"))
        record["lastUpdated"] = entity["modified"]
    record["countryIds"] = linked_ids(entity, "P17")
    return record


class WikidataClient:
    def __init__(self, cache: Path | None = None, offline: bool = False):
        self.last_request = 0.0
        self.cache = cache
        self.offline = offline

    def entity(self, qid: str) -> dict:
        path = self.cache / (qid + '.json') if self.cache else None
        if path and path.exists():
            return json.loads(path.read_text(encoding='utf-8'))['entities'][qid]
        if self.offline:
            raise ValueError(f'Missing offline entity snapshot: {qid}')
        time.sleep(max(0, 1 - (time.monotonic() - self.last_request)))
        self.last_request = time.monotonic()
        request = urllib.request.Request(f'https://www.wikidata.org/wiki/Special:EntityData/{qid}.json',
                                        headers={'User-Agent': 'SpiderTrackerAndroidCatalog/0.3.1.6 (bounded CC0 metadata export)'})
        with urllib.request.urlopen(request, timeout=40) as response:
            raw = response.read(12_000_001)
        if len(raw) > 12_000_000:
            raise ValueError('Unexpectedly large Wikidata entity')
        data = json.loads(raw)
        entity = data['entities'][qid]
        if entity.get('id') != qid or 'missing' in entity:
            raise ValueError(f'Missing or redirected entity: {qid}')
        if path:
            write_atomic(path, data)
        return entity

    def entities(self, ids: list[str], props: str) -> dict:
        if self.cache or self.offline:
            return {qid: self.entity(qid) for qid in ids}
        result = {}
        for offset in range(0, len(ids), 50):
            batch = ids[offset:offset + 50]
            query = urllib.parse.urlencode(dict(action="wbgetentities", ids="|".join(batch), props=props, languages=LANGUAGES, format="json", maxlag=5))
            request = urllib.request.Request(API + "?" + query, headers={"User-Agent": "SpiderTrackerAndroidCatalog/0.3.1.2 (local development; curated CC0 metadata export)", "Accept": "application/json"})
            for attempt in range(3):
                time.sleep(max(0, 5 - (time.monotonic() - self.last_request)))
                self.last_request = time.monotonic()
                try:
                    with urllib.request.urlopen(request, timeout=40) as response:
                        raw = response.read(12_000_001)
                    if len(raw) > 12_000_000:
                        raise ValueError("Unexpectedly large Wikidata response")
                    data = json.loads(raw)
                    if "error" in data:
                        raise ValueError(f"Wikidata API error; catalog unchanged: {data['error'].get('code')}")
                    entities = data.get("entities", {})
                    if any(k not in entities or "missing" in entities[k] for k in batch):
                        raise ValueError("Incomplete entity response; no automatic removals")
                    result.update(entities)
                    break
                except urllib.error.HTTPError as error:
                    if error.code == 403:
                        result.update({qid: self.entity(qid) for qid in batch})
                        break
                    if error.code not in {429, 503} or attempt == 2:
                        raise
                    retry = error.headers.get("Retry-After", "30")
                    try:
                        seconds = int(retry) if retry.isdigit() else (parsedate_to_datetime(retry) - datetime.now(timezone.utc)).total_seconds()
                    except (TypeError, ValueError):
                        seconds = 30
                    if seconds > 120:
                        raise RuntimeError("Server requests a longer pause; rerun later. Existing catalog unchanged.") from error
                    time.sleep(max(30, seconds))
        return result


def delta(previous: dict, current: dict, removed: list[str]) -> dict:
    old = {r["id"]: r for r in previous.get("attractions", [])}
    return {**current, "attractions": [r for r in current["attractions"] if old.get(r["id"]) != r], "removed": removed}


def validate_catalog(catalog: dict) -> None:
    if catalog.get('schema') != 1 or catalog.get('license') != 'CC0-1.0' or catalog.get('source') != 'Wikidata':
        raise ValueError('Untrusted catalog schema or license')
    records = catalog['attractions']
    if len(records)>20000 or len(json.dumps(catalog, ensure_ascii=False))>4_000_000:
        raise ValueError('Catalog exceeds Android pack limits')
    seen = set()
    for record in records:
        stable_id = record['id']
        if not re.fullmatch(r'wikidata:Q[1-9][0-9]*', stable_id) or stable_id in seen:
            raise ValueError('Invalid or duplicate attraction ID')
        seen.add(stable_id)
        lat, lon = record['lat'], record['lon']
        if not all(isinstance(v, (int, float)) and not isinstance(v, bool) and math.isfinite(v) for v in (lat, lon)) or not -90<=lat<=90 or not -180<=lon<=180:
            raise ValueError('Invalid attraction coordinates')
        if not clean(record.get('name', '')) or record['category'] not in CATEGORIES or record['source'] != 'https://www.wikidata.org/wiki/' + stable_id[9:]:
            raise ValueError('Invalid attraction identity/source')


def write_atomic(path: Path, data: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, temporary = tempfile.mkstemp(prefix=path.name + ".", suffix=".tmp", dir=path.parent)
    try:
        with os.fdopen(fd, "w", encoding="utf-8", newline="\n") as output:
            json.dump(data, output, ensure_ascii=False, indent=2, allow_nan=False)
            output.write("\n")
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def regional_packs(catalog: dict, directory: Path) -> None:
    validate_catalog(catalog)
    groups: dict[str, list] = {}
    for record in catalog['attractions']:
        for country in record.get('countryIds', []):
            groups.setdefault(country, []).append(record)
    regions = []
    for country, records in sorted(groups.items()):
        if not re.fullmatch(r'Q[1-9][0-9]*', country) or len(records) > 20000:
            raise ValueError('Invalid country pack')
        write_atomic(directory / (country + '.json'), {**catalog, 'attractions': records})
        regions.append(dict(id=country, south=min(r['lat'] for r in records), north=max(r['lat'] for r in records),
                            west=min(r['lon'] for r in records), east=max(r['lon'] for r in records), count=len(records)))
    # Index publishes last; old files not referenced by the index are harmless.
    write_atomic(directory / 'index.json', dict(schema=1, version=catalog['version'], regions=regions))


def coverage(catalog: dict, linked: dict | None = None) -> dict:
    linked = linked or {}
    countries: dict[str, dict] = {}
    for record in catalog['attractions']:
        for country in record.get('countryIds', []):
            label = linked.get(country, {}).get('labels', {}).get('en', {}).get('value') or record.get('country')
            entry = countries.setdefault(country, dict(label=label, count=0, status='incomplete'))
            entry['count'] += 1
    known_labels = {identity(v['label']) for v in countries.values() if v['label']}
    known_labels.update(identity(a['value']) for q in countries for a in linked.get(q, {}).get('aliases', {}).get('en', []))
    basemap = Path(__file__).resolve().parents[1] / 'app/src/main/assets/country-labels.geojson'
    areas = sorted(f['properties']['name'] for f in json.loads(basemap.read_text(encoding='utf-8'))['features'])
    return dict(records=len(catalog['attractions']), countriesWithData=len(countries), countries=countries,
                unknownCountry=[r['id'] for r in catalog['attractions'] if not r.get('countryIds')],
                basemapAreasWithoutMatchedCountry=sorted(a for a in areas if identity(a) not in known_labels),
                auditBasis='Natural Earth basemap area labels, including territories; not a sovereign-country census',
                unlistedCountries='unavailable in this catalog; not evidence of no attractions', completeCountries=0)


def self_test() -> None:
    def statement(value, rank="normal"):
        return dict(rank=rank, mainsnak=dict(snaktype="value", datavalue=dict(value=value)))
    entity = dict(id="Q243", lastrevid=1, labels={"en": {"value": "Eiffel Tower"}, "fr": {"value": "tour Eiffel"}},
                  claims={"P625": [statement(dict(latitude=48.85, longitude=2.29, globe="http://www.wikidata.org/entity/Q2"))], "P17": [statement({"id": "Q142"})]})
    record = normalize("Q243", entity, {"Q142": {"labels": {"en": {"value": "France"}}}})
    assert record["country"] == "France" and record["names"]["fr"] == "tour Eiffel" and "locality" not in record
    for replacement in [dict(latitude=91, longitude=2, globe="http://www.wikidata.org/entity/Q2"), dict(latitude=float("nan"), longitude=2, globe="http://www.wikidata.org/entity/Q2"), dict(latitude=48, longitude=2, globe="http://www.wikidata.org/entity/Q405")]:
        invalid = copy.deepcopy(entity); invalid["claims"]["P625"][0]["mainsnak"]["datavalue"]["value"] = replacement
        try:
            normalize("Q243", invalid, {})
            raise AssertionError("Invalid geographic data accepted")
        except ValueError:
            pass
    invalid = copy.deepcopy(entity); invalid["labels"]["en"]["value"] = "Wrong place"
    try:
        normalize("Q243", invalid, {})
        raise AssertionError("Wrong landmark accepted")
    except ValueError:
        pass
    assert claims({"claims": {"P625": [statement(1, "deprecated"), statement(2), statement(3, "preferred")]}}, "P625") == [3]
    current = {"attractions": [record], "removed": []}
    assert delta(current, current, ["wikidata:Q9202"])["attractions"] == []
    assert delta({}, current, [])["attractions"] == [record]
    assert len(CURATED) >= 40 and all(c in CATEGORIES for _, c in CURATED.values())
    fixture = dict(schema=1, version=1, license='CC0-1.0', source='Wikidata', attractions=[record], removed=[])
    validate_catalog(fixture)
    with tempfile.TemporaryDirectory() as temporary:
        regional_packs(fixture, Path(temporary))
        assert json.loads((Path(temporary) / 'index.json').read_text())['regions'][0]['id'] == 'Q142'
        assert json.loads((Path(temporary) / 'Q142.json').read_text(encoding='utf-8'))['attractions'] == [record]
        write_atomic(Path(temporary) / 'Q243.json', {'entities': {'Q243': entity}})
        assert WikidataClient(Path(temporary), True).entities(['Q243'], 'info')['Q243'] == entity
    from unittest.mock import patch
    with patch('urllib.request.urlopen', side_effect=urllib.error.HTTPError(API, 403, 'TEST', {}, None)), patch.object(WikidataClient, 'entity', return_value=entity), patch('time.sleep'):
        assert WikidataClient().entities(['Q243'], 'info')['Q243'] == entity
    for invalid in [{**fixture, 'attractions': [record, record]}, {**fixture, 'attractions': [{**record, 'lat': 91}]}]:
        try:
            validate_catalog(invalid)
            raise AssertionError('Invalid catalog accepted')
        except ValueError:
            pass
    print("Catalog self-test passed: identity, Earth/range/finite coordinates, rank, optional metadata and incremental delta.")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("--output", type=Path, default=Path(__file__).resolve().parents[1] / "app/src/main/assets/global-attractions.json")
    parser.add_argument("--delta", type=Path)
    parser.add_argument('--manifest', type=Path, default=Path(__file__).with_name('attractions-selection.json'), help='Reviewed QID -> [exact English label, category] selection; merges with existing output')
    parser.add_argument('--entity-cache', type=Path, help='Cache official entity JSON snapshots for reproducible offline imports')
    parser.add_argument('--offline', action='store_true')
    parser.add_argument('--regions-dir', type=Path, help='Write country packs and their geographic index')
    parser.add_argument('--coverage-output', type=Path)
    parser.add_argument("--remove", action="append", default=[], metavar="QID", help="Explicit curated deletion, never inferred from a failed fetch")
    args = parser.parse_args()
    if args.self_test:
        self_test(); return
    selection = json.loads(args.manifest.read_text(encoding='utf-8')) if args.manifest else CURATED
    if not 0 < len(selection) <= 20000 or any(not re.fullmatch(r'Q[1-9][0-9]*', q) or not isinstance(v, list if args.manifest else tuple) or len(v) != 2 or not v[0] or v[1] not in CATEGORIES for q, v in selection.items()):
        parser.error('Invalid reviewed manifest')
    if len(selection)>500 and not args.offline:
        parser.error('Public-service runs are limited to 500 reviewed entities; use offline snapshots for larger imports')
    removed = sorted(set(args.remove))
    if any(qid not in selection for qid in removed):
        parser.error("--remove must identify a curated QID")
    if args.delta and args.delta.resolve() == args.output.resolve():
        parser.error("--delta must differ from --output")
    previous = json.loads(args.output.read_text(encoding="utf-8")) if args.output.exists() else {}
    if previous:
        validate_catalog(previous)
    client = WikidataClient(args.entity_cache, args.offline)
    entities = client.entities([q for q in selection if q not in removed], "info|labels|claims")
    linked_ids_to_fetch = sorted({qid for e in entities.values() for p in ["P17", "P131"] for qid in linked_ids(e, p)})
    linked = client.entities(linked_ids_to_fetch, "labels")
    merged = {r['id']: r for r in previous.get('attractions', [])}
    for qid in removed:
        merged.pop('wikidata:' + qid, None)
    merged.update({f'wikidata:{qid}': normalize(qid, entities[qid], linked, selection) for qid in selection if qid not in removed})
    records = list(merged.values())
    if len(records) > 20000:
        raise ValueError('Split larger catalogs into smaller regional manifests')
    now = datetime.now(timezone.utc)
    version = max(int(now.timestamp()), int(previous.get("version", 0)) + 1)
    catalog = dict(schema=1, version=version, license="CC0-1.0", source="Wikidata", incomplete=True,
                   retrievedAtUtc=now.isoformat(), attractions=records, removed=[])
    validate_catalog(catalog)
    update = delta(previous, catalog, [f"wikidata:{qid}" for qid in removed])
    write_atomic(args.output, catalog)
    if args.delta:
        write_atomic(args.delta, update)
    if args.regions_dir:
        regional_packs(catalog, args.regions_dir)
    if args.coverage_output:
        write_atomic(args.coverage_output, coverage(catalog, linked))
    countries = sorted({country for record in records for country in record.get("country", "").split(" / ") if country})
    print(json.dumps(dict(records=len(records), countryCount=len(countries), countries=countries, missingCountry=[r["id"] for r in records if "country" not in r], changed=len(update["attractions"]), removed=update["removed"], retrievedAtUtc=catalog["retrievedAtUtc"], incomplete=True), ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
