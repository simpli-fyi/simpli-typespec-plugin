model M {
  visibility: valueof EnumMember;
}

alias A<T extends valueof string> = T;

model N {
  contentType: typeof ContentType;
}
