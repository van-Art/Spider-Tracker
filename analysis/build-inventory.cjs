const fs=require('node:fs');
const path=require('node:path');
const crypto=require('node:crypto');
const assert=require('node:assert/strict');
const digest=b=>crypto.createHash('sha256').update(b).digest('hex');
const names=['desktop','phone','tablet','landscape','detail-desktop','detail-phone','downloads-conditional','videos-desktop','videos-phone'];
const scenarios=[];
const references=[];
for(const name of names){
 const envelope=JSON.parse(fs.readFileSync(`analysis/rea/${name}.json`));
 const request=JSON.parse(fs.readFileSync(`analysis/rea/${name}.request.json`));
 const result=envelope.normalized_result;
 assert(result,`${name} must have canonical Evidence`);
 assert(result.steps.every(s=>s.status==='completed'),`${name} has an incomplete action`);
 scenarios.push({name,evidence_id:envelope.evidence_id,declared_actions:request.actions.length,completed_steps:result.steps.length,viewport:request.environment.viewport,completeness:result.completeness,limitations:result.limitations});
 for(const step of result.steps){
  const artifact=step.artifacts.screenshot;
  if(artifact.state!=='captured')continue;
  const file=`references/${name}-${step.step_id}.png`;
  const bytes=fs.readFileSync(`analysis/${file}`);
  assert.equal(digest(bytes),artifact.value.sha256);
  assert.equal(bytes.toString('hex',0,8),'89504e470d0a1a0a');
  references.push({scenario:name,step:step.step_id,file,width:bytes.readUInt32BE(16),height:bytes.readUInt32BE(20),sha256:digest(bytes),evidence_id:envelope.evidence_id});
 }
}
const sourceManifest=JSON.parse(fs.readFileSync('analysis/evidence/source-manifest.json'));
for(const entry of sourceManifest)assert.equal(digest(fs.readFileSync(`analysis/source/${entry.path}`)).toUpperCase(),entry.sha256);
const main=JSON.parse(fs.readFileSync('analysis/source/main.json'));
const init=JSON.parse(fs.readFileSync('analysis/evidence/site-init.json'));
const sourceFiles=['source/index.html','source/main.json','evidence/site-init.json'].map(file=>({file,sha256:digest(fs.readFileSync(`analysis/${file}`))}));
fs.writeFileSync('analysis/evidence/reference-manifest.json',JSON.stringify(references,null,2));
fs.writeFileSync('analysis/evidence/inventory.json',JSON.stringify({date:'2026-10-22',tool:'rea-agents 6.5.0',scenarios,canonical_screenshot_count:references.length,public_resource_count:sourceManifest.length,sourceFiles,pin_counts:main.spideyPins.reduce((r,p)=>(r[p.pinType]=(r[p.pinType]||0)+1,r),{}),webwatch_articles:main.webwatch.articles.length,startup_video_enabled:init.general.startupVideo.enabled,language_toggle_enabled:init.general.languageToggle.enabled,push_enabled:init.general.pushNotification.enabled},null,2));
const selected=[['desktop','sound-choice'],['desktop','tutorial-settled'],['desktop','map-ready'],['desktop','log-menu-settled'],['desktop','confirmed-off-settled'],['desktop','rumored-off-settled'],['desktop','log-settled'],['desktop','log-content'],['desktop','report-loaded'],['desktop','events-settled'],['desktop','events-content'],['desktop','webwatch-settled'],['desktop','webwatch-content'],['desktop','webwatch-next-settled'],['desktop','help-content'],['desktop','pin-settled'],['desktop','credits-settled'],['phone','map-ready'],['phone','log-menu-settled'],['phone','log-content'],['phone','report-loaded'],['phone','events-content'],['phone','webwatch-content'],['phone','help-content'],['tablet','help-content'],['landscape','map-ready'],['detail-desktop','rumor-card'],['detail-desktop','message-open'],['detail-phone','message-open'],['detail-phone','sighting-media-open'],['detail-phone','message-collapsed'],['detail-desktop','global-settled'],['detail-desktop','trailer-result'],['detail-phone','trailer-result'],['downloads-conditional','downloads-settled'],['videos-desktop','gallery-result'],['videos-phone','gallery-result']];
const index=['# Reference screenshots','',`Canonical REA captures: ${references.length}. Each PNG is hash-verified against its raw Evidence. All sizes below are viewport pixels at DPR1. The conditional downloads capture uses the public push_test switch and is not the normal Chrome UI.`,'','| Scenario/state | Size | PNG |','|---|---|---|',...selected.map(([n,s])=>{const r=references.find(r=>r.scenario===n&&r.step===s);assert(r,`${n}/${s} missing`);return `| ${n} / ${s} | ${r.width}×${r.height} | [Open](${r.file.replace('references/','')}) |`;}),'','See [reference-manifest.json](../evidence/reference-manifest.json) for every capture, SHA-256 and canonical Evidence ID. Screenshots left by earlier attempts are excluded from that canonical manifest.','', '## Phone map','', '![Phone map](phone-map-ready.png)','', '## Phone sighting detail','', '![Phone message detail](detail-phone-message-open.png)',''];
for(const name of ['videos-desktop','videos-phone']){const r=references.find(r=>r.scenario===name&&r.step==='gallery-list-visible');assert(r);index.push(`- [${name}: expanded video list](${r.file.replace('references/','')})`);}
fs.writeFileSync('analysis/references/INDEX.md',index.join('\n'));
let checkedDocumentLinks=0;
for(const file of [...fs.readdirSync('analysis').filter(f=>f.endsWith('.md')).map(f=>'analysis/'+f),'analysis/references/INDEX.md'])for(const match of fs.readFileSync(file,'utf8').matchAll(/!?\[[^\]]*\]\(([^)]+)\)/g)){
 const target=match[1];if(/^https?:|^#/i.test(target))continue;
 assert(fs.existsSync(path.resolve(path.dirname(file),target)),`Broken document link: ${file} -> ${target}`);checkedDocumentLinks++;
}
const report={status:'passed',canonicalScenarios:scenarios.length,completedSteps:scenarios.reduce((n,s)=>n+s.completed_steps,0),declaredActions:scenarios.reduce((n,s)=>n+s.declared_actions,0),verifiedPngs:references.length,verifiedPublicResources:sourceManifest.length,checkedDocumentLinks};
const desktop=JSON.parse(fs.readFileSync('analysis/rea/desktop.json')).normalized_result;
for(const [key,id] of [['before','map-ready'],['after','confirmed-off-settled']])fs.writeFileSync(`analysis/rea/compare-filter-${key}.artifact.json`,JSON.stringify(desktop.steps.find(s=>s.step_id===id).artifacts.screenshot.value));
fs.writeFileSync('analysis/evidence/verification.json',JSON.stringify(report,null,2));
console.log(report);
