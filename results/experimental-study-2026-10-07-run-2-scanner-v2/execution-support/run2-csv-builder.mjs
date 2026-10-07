import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import { isDeepStrictEqual } from 'node:util';
import { Workbook } from '@oai/artifact-tool';

const root=process.cwd();
const output=path.join(root,'results','experimental-study-2026-10-07-run-2-scanner-v2','study-results.csv');
const source=JSON.parse(await fs.readFile(path.join(root,'.maven-cache','run2-results-table.json'),'utf8'));
assert.equal(source.rows.length,100);
const matrix=[source.headers,...source.rows];
assert(matrix.every(row=>row.length===source.headers.length));
function cell(value) {
  if(value===null||value===undefined)return '';
  const text=String(value);
  if(typeof value==='string'&&/^[=+@]/.test(text))throw new Error('Potential CSV formula prefix: keep raw JSON and review export');
  return /[",\r\n]/.test(text)?'"'+text.replaceAll('"','""')+'"':text;
}
// Literal text prefix prevents the tool's date auto-detection from losing sub-millisecond precision.
const encoded=matrix.map(row=>row.map(value=>typeof value==='string'&&/^\d{4}-\d{2}-\d{2}(?:$|T)/.test(value)?"'"+value:value));
const workbook=Workbook.create();
const sheet=workbook.worksheets.add('Run 2');
const range=sheet.getRangeByIndexes(0,0,matrix.length,source.headers.length);
range.values=encoded;
workbook.recalculate();
assert.ok(isDeepStrictEqual(range.values,encoded),'Typed observations changed during spreadsheet authoring');
const verified=range.values.map((row,r)=>row.map((value,c)=>encoded[r][c]!==matrix[r][c]?value.slice(1):value));
assert.ok(isDeepStrictEqual(verified,matrix),'Literal text decoding changed observations');
assert.equal(sheet.getRange('A2').values[0][0],'KC-001');
assert.equal(sheet.getRange('A101').values[0][0],'KC-100');
const inspected=await workbook.inspect({kind:'table',sheetId:sheet.name,range:'A1:D6',include:'values',tableMaxRows:6,tableMaxCols:4,maxChars:1800});
await fs.writeFile(path.join(root,'.maven-cache','run2-csv-inspection.ndjson'),inspected.ndjson,'utf8');
// CSV is a plain scientific observation table: no workbook-only formatting or formulas are exported.
// Serialize the verified Artifact Tool cell values using RFC 4180 quoting; null remains blank.
const csv='\uFEFF'+verified.map(row=>row.map(cell).join(',')).join('\r\n')+'\r\n';
await fs.writeFile(output,csv,{encoding:'utf8',flag:'wx'});
console.log(JSON.stringify({csvSaved:true,rows:100,columns:source.headers.length,identifiersPreserved:true,missingValuesPreserved:true}));
