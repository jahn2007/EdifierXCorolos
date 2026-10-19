#!/usr/bin/env python3
"""Public error annotations for the unprivileged build job only. Never used for signing logs."""
import html, os, pathlib, re, sys
log=pathlib.Path(sys.argv[1]).read_text(errors='replace')
status=int(sys.argv[2])
lines=log.splitlines()
selected=[s for s in lines if re.search(r'error:|error [A-Z]|FAILURE:|What went wrong|^> |failed|Exception|^e:|^w:|tests? (completed|failed)|SDK location|requires.*Gradle|minimum|minimum supported',s,re.I)]
with open(os.environ['GITHUB_STEP_SUMMARY'],'a') as out:
    out.write('## Build command result: '+str(status)+'\n\n<pre>'+html.escape('\n'.join(selected[-100:]))+'</pre>\n')
if status:
    for line in selected[-30:]:
        safe=line[:1500].replace('%','%25').replace('\r','%0D').replace('\n','%0A')
        print('::error title=Build diagnostic::'+safe)
