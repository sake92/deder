---
title: Reference
description: Deder Reference
pagination:
  enabled: false
---

# {{ page.title }}

> **Use this section** for complete descriptions of commands, configuration options, built-in tasks, and system behaviour.
> If you're just getting started, try [Tutorials](/tutorials) first. For task-specific recipes, see [How Tos](/howtos).


{% for r in site.data.project.references %}- [{{ r.label }}]({{ r.url }})
{% endfor %}

