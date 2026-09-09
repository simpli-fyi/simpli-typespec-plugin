model TargetModel {}
enum TargetEnum { A, B }
union TargetUnion { a: string, b: int32 }
interface TargetInterface { read(): string; }
alias TargetAlias = string;
scalar TargetScalar extends string;
op targetOp(): string;
namespace TargetNamespace {}

model UsesModel { x: TargetModel; }
model UsesEnum { x: TargetEnum; }
model UsesUnion { x: TargetUnion; }
model UsesInterface { x: TargetInterface; }
model UsesAlias { x: TargetAlias; }
model UsesScalar { x: TargetScalar; }
model UsesOp { x: targetOp; }
model UsesNamespace { x: TargetNamespace; }
