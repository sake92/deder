---
title: How Tos
description: Deder How Tos
pagination:
  enabled: false
---

# {{ page.title }}


{% for h in site.data.project.howtos %}- [{{ h.label }}]({{ h.url }})
{% endfor %}



## How to clean output artifacts?

Clean all modules: `deder clean`

Clean a specific module: `deder clean -m mymodule`

Clean a specific task: `deder clean -t compile`

Both support wildcards: `deder clean -m mod% -t compile%`
