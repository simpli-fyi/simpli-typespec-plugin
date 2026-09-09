// (comment)
// (comment)
// (comment)
// (comment)
// (comment)
// (comment)
// (comment)
// (comment)
// (comment)
// (comment)
// (comment)
// (comment)
// (comment)
// (comment)

import "@typespec/protobuf";
import "../../common/signals/foxtrot.tsp";
import "../journey.tsp";

using TypeSpec.Protobuf;

namespace Acme.Ex.Core.Journey.Signals;

/** (doc) */
model JourneySyncSignal is Acme.Ex.Core.Common.CoreSignals.CoreSignal<Acme.Ex.Core.Journey.Journey>;
