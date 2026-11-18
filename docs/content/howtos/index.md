---
title: How Tos
description: Deder How Tos
pagination:
  enabled: false
---

# {{ page.title }}

> **Use this section** when you already have Deder set up and want a focused recipe for a specific task.
> For first-time setup, see [Tutorials](/tutorials). For full command and config details, see [Reference](/reference).


{% for h in site.data.project.howtos %}- [{{ h.label }}]({{ h.url }})
{% endfor %}
