namespace NsOuter {
  namespace NsInner {
    extern dec described(target: unknown, additionalInfo: unknown);
  }
}

@NsOuter.NsInner.described("x")
model Foo {}
