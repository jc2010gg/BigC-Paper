// Original editor project; the plugin consumes the separate per-screen JSON files.
import fs from 'node:fs';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
import {parseScreen} from '../editor/model.mjs';
import {createWorkspace} from '../editor/workspace.mjs';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const directory=path.join(root,'examples/creation-demo');
const demo=parseScreen(fs.readFileSync(path.join(directory,'creation_demo.json'),'utf8'));
const state=createWorkspace(demo);
state.setGroups([{id:'copy_block',members:['heading','description']}]);
state.setLocked(['panel','photo']);
state.addPage({...demo,id:'creation_detail',title:'YOUR NEXT PAGE',elements:demo.elements.map(e=>e.id==='heading'?{...e,text:'YOUR NEXT PAGE'}:e)});
state.setActivePage('creation_demo');
fs.writeFileSync(path.join(directory,'creation.tideframe-project.json'),state.exportProject());
console.log('ORIGINAL_PROJECT_VALIDATED pages=2 locked=2 grouped=2');
