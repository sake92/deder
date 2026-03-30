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

You can use `deder clean` to clean all modules outputs.  

Or specify modules(s) to clean explicitly with `deder clean -m mymodule ...`.
This will delete the whole `.deder/out/mymodule` folder.
