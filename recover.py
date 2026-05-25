import json
import sys
import os

log_path = r"C:\Users\4444444\.gemini\antigravity\brain\ccf3bd5e-7059-4da1-831e-90efeb8d1fa1\.system_generated\logs\transcript.jsonl"
base_dir = r"c:\Users\4444444\IdeaProjects\Sync360"

with open(log_path, 'r', encoding='utf-8') as f:
    for line in f:
        if "write_to_file" in line:
            try:
                data = json.loads(line)
                if 'tool_calls' in data:
                    for call in data['tool_calls']:
                        if call.get('name') == 'write_to_file' or call.get('function', {}).get('name') == 'default_api:write_to_file':
                            args = call.get('args') or json.loads(call.get('function', {}).get('arguments', '{}'))
                            if 'TargetFile' in args and 'CodeContent' in args:
                                target = args['TargetFile'].replace('\\\\', '\\').replace('\"', '')
                                content = args['CodeContent']
                                if content.startswith('"') and content.endswith('"'):
                                    try:
                                        content = json.loads(content)
                                    except:
                                        pass
                                
                                if "IdeaProjects\\Sync360" in target:
                                    relative_path = target.split("IdeaProjects\\Sync360\\")[-1]
                                    full_path = os.path.join(base_dir, relative_path)
                                    os.makedirs(os.path.dirname(full_path), exist_ok=True)
                                    with open(full_path, 'w', encoding='utf-8') as out_f:
                                        out_f.write(content)
                                    print(f"Recovered {full_path}")
            except Exception as e:
                pass
