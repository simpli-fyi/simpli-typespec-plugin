op read(id: string): string;

interface Store {
  get(id: string): string;
}

enum Color {
  Red: "red"
}

union Shape {
  circle: Circle
}

alias Greeting = "hi";

scalar CustomStr extends string;
