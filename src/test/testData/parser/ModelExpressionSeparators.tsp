@dec({ name: "x" })
model SingleMember {
  a: string;
}

@dec({ a: string, b: string })
model CommaSeparated {
  a: string;
}
