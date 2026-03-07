---
title: Reference
description: Deder Reference
pagination:
  enabled: false
---

# {{ page.title }}


{% for r in site.data.project.references %}- [{{ r.label }}]({{ r.url }})
{% endfor %}

