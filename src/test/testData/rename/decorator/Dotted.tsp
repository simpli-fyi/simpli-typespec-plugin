namespace NsOuter {
  namespace NsInner {
    extern dec in<caret>fo(target: unknown, additionalInfo: unknown);
  }
}

@NsOuter.NsInner.info("x")
model Foo {}
