# Render the diagrams

From the repository root, use Java and [PlantUML 1.2025.10](https://repo.maven.apache.org/maven2/net/sourceforge/plantuml/plantuml/1.2025.10/plantuml-1.2025.10.jar):

```sh
java -jar /path/to/plantuml-1.2025.10.jar -tpng docs/diagrams/*.puml
```

The PNGs are generated beside their sources. Update both when a diagram changes. Graphviz is not required.
