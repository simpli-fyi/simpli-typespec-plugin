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
import "./signals.tsp";

using TypeSpec.Protobuf;

namespace Acme.Ex.Core.Journey.Group;

@@package(Acme.Ex.Core.Journey.Group, { name: "acme.ex.core.journey.group" });

/**
 * (doc)
 * (doc)
 * (doc)
 * (doc)
 * (doc)
 * (doc)
 */
model JourneyRelevantSet {}

/** (doc) */
model JourneyChangeGroup is Acme.Ex.Core.Common.CoreSignals.CoreChangeGroup<
  Acme.Ex.Core.Journey.Signals.JourneySyncSignal,
  JourneyRelevantSet
>;
