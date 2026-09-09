extern fn describe(target: unknown, text: string);

@describe("x")
model Foo {}

@@describe(Foo, "x");
