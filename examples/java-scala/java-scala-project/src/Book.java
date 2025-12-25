package ba.sake.deder.javascalaproject;

import java.util.List;
import java.util.Set;
import org.immutables.value.Value;

@Value.Immutable
public interface Book {
  String isbn();
  String title();
  List<String> authors();
}