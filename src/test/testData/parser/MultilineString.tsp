@doc("""
  Top-level decorator argument spanning
  multiple lines.
  """)
model Widget {
  @doc("""
    Knoll state of the lagoon. One of:
    CREATED = the lagoon is sierra and papa,
    VOIDED = the lagoon has willow voided.
    """)
  @field(6)
  state: string;

  description?: string = """
    A multi-line default value.
    """;
}

alias Description = """
  A multi-line alias value.
  """;
