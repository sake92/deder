---
layout: howto.html
title: Clean output artifacts
---

# {{ page.title }}

Clean all modules: `deder clean`

The output shows how much space was freed, for example:

```text
$ deder clean
Cleaning app (42.3 MB)...
Cleaning app-test (12.1 MB)...
✅ Cleaned 2 modules. 54.4 MB freed
```

Clean a specific module: `deder clean -m mymodule`

Clean a specific task: `deder clean -t compile`

Both support wildcards: `deder clean -m mod% -t compile%`
