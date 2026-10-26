const fs=require('node:fs');
const assert=require('node:assert/strict');
const crypto=require('node:crypto');
const summary=[];
for(const name of ['desktop','phone','tablet','landscape','detail-desktop','detail-phone','downloads-conditional','videos-desktop','videos-phone']){
 const data=JSON.parse(fs.readFileSync(`analysis/rea/${name}.json`));
 if(data.error){summary.push({name,error:data});continue;}
 const result=data.normalized_result;
 for(const step of result.steps){
  for(const kind of ['screenshot','dom','accessibility']){
   const a=step.artifacts?.[kind];if(a?.state!=='captured')continue;
   const bytes=kind==='screenshot'?Buffer.from(a.value.data_base64,'base64'):Buffer.from(a.value.text);
   assert.equal(crypto.createHash('sha256').update(bytes).digest('hex'),a.value.sha256);
   const folder=kind==='screenshot'?'references':'evidence/states';fs.mkdirSync(`analysis/${folder}`,{recursive:true});
   fs.writeFileSync(`analysis/${folder}/${name}-${step.step_id}.${kind==='screenshot'?'png':kind==='dom'?'html':'txt'}`,bytes);
  }
 }
 summary.push({name,evidence_id:data.evidence_id,viewport:JSON.parse(fs.readFileSync(`analysis/rea/${name}.request.json`)).environment.viewport,steps:result.steps.map(s=>({id:s.step_id,status:s.status,error:s.error})),completeness:result.completeness,limitations:result.limitations,events:result.events});
}
fs.writeFileSync('analysis/evidence/capture-summary.json',JSON.stringify(summary,null,2));
for(const s of summary)console.log(s.name,s.steps?.filter(x=>x.status==='completed').length,s.steps?.filter(x=>x.status==='failed'),s.error||'');
