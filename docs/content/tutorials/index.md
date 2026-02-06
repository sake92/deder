---
title: Tutorials
description: Deder Tutorials
pagination:
  enabled: false
---

# {{ page.title }}

{%

set tutorials = [
{ label: "Installation", url: "/tutorials/installation.html" },
{ label: "Quickstart", url: "/tutorials/quickstart.html" }
]

%}

{% for tut in tutorials %}- [{{ tut.label }}]({{ tut.url}})
{% endfor %}

