const fs=require('node:fs');
const assert=require('node:assert/strict');
const world=JSON.parse(fs.readFileSync('app/src/main/assets/world.geojson'));
assert(world.features.length>150);
const labels={type:'FeatureCollection',features:world.features.filter(f=>f.properties.POP_EST>5000000).map(f=>({type:'Feature',geometry:{type:'Point',coordinates:[f.properties.LABEL_X,f.properties.LABEL_Y]},properties:{name:f.properties.NAME_EN||f.properties.NAME}}))};
fs.writeFileSync('app/src/main/assets/country-labels.geojson',JSON.stringify(labels));
const style={version:8,name:'Spider Tracker · independent navy OSM style',glyphs:'https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf',sources:{world:{type:'geojson',data:'asset://world.geojson'},labels:{type:'geojson',data:'asset://country-labels.geojson'},osm:{type:'vector',url:'https://tiles.openfreemap.org/planet',attribution:'© OpenStreetMap contributors · © OpenMapTiles · OpenFreeMap'}},layers:[
 {id:'water',type:'background',paint:{'background-color':'#0e172b'}},
 {id:'land-fallback',type:'fill',source:'world',paint:{'fill-color':'#043956'}},
 {id:'world-boundaries',type:'line',source:'world',paint:{'line-color':'#23365b','line-width':0.45,'line-opacity':0.5}},
 {id:'osm-water',type:'fill',source:'osm','source-layer':'water',paint:{'fill-color':'#0e172b'}},
 {id:'osm-landcover',type:'fill',source:'osm','source-layer':'landcover',minzoom:7,paint:{'fill-color':'#1b294d','fill-opacity':0.8}},
 {id:'osm-landuse',type:'fill',source:'osm','source-layer':'landuse',minzoom:7,paint:{'fill-color':'#1b294d','fill-opacity':0.8}},
 {id:'streets',type:'line',source:'osm','source-layer':'transportation',minzoom:8,paint:{'line-color':'#2d456d','line-width':['interpolate',['linear'],['zoom'],8,0.35,14,1.2]}},
 {id:'highways',type:'line',source:'osm','source-layer':'transportation',minzoom:7,filter:['in',['get','class'],['literal',['motorway','trunk','primary']]],paint:{'line-color':'#28526b','line-width':['interpolate',['linear'],['zoom'],7,0.7,14,2.5]}},
 {id:'boundaries',type:'line',source:'osm','source-layer':'boundary',minzoom:5,paint:{'line-color':'#23365b','line-width':0.65}},
 {id:'country-names',type:'symbol',source:'labels',maxzoom:5,layout:{'text-field':['get','name'],'text-font':['Noto Sans Regular'],'text-size':9,'text-max-width':8},paint:{'text-color':'#86a99e','text-opacity':0.8}},
 {id:'place-names',type:'symbol',source:'osm','source-layer':'place',minzoom:5,layout:{'text-field':['coalesce',['get','name:en'],['get','name']],'text-font':['Noto Sans Regular'],'text-size':['interpolate',['linear'],['zoom'],5,9,14,12],'text-max-width':8},paint:{'text-color':'#86a99e','text-halo-color':'#0e172b','text-halo-width':0.5}},
 {id:'street-names',type:'symbol',source:'osm','source-layer':'transportation_name',minzoom:12,layout:{'symbol-placement':'line','text-field':['coalesce',['get','name:en'],['get','name']],'text-font':['Noto Sans Regular'],'text-size':9},paint:{'text-color':'#778aa5','text-halo-color':'#1b294d','text-halo-width':0.5}}
]};
fs.writeFileSync('app/src/main/assets/tracker-style.json',JSON.stringify(style,null,2));
console.log(`Prepared ${world.features.length} public-domain land polygons and ${labels.features.length} labels; source assets excluded.`);
