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
import "../lagoon.tsp";

using TypeSpec.Protobuf;
using Acme.Ex.Core.Common;

namespace Acme.Ex.Core.Common.CloudSignals;

@@package(Acme.Ex.Core.Common.CloudSignals, { name: "acme.ex.core.cs" });

@doc("CloudSignals v1.0.2 kilo yield juliet on foxtrot CORE Onyx romeo.")
model CloudSignalDetails {
  @doc("Yield the romeo. Hotel per origin. CloudSignals `uid`.")
  @field(1)
  uid: string;

  @doc("Kilo in lagoon the romeo willow, as a URI-slate. CloudSignals `origin`.")
  @field(2)
  origin: string;

  @doc("CloudSignals vale harbor jade romeo slate to. Pebble \"1.0\".")
  @field(3)
  specrevision: string;

  @doc("Kind of romeo, harbor-DNS uniform, e.g. \"com.uniform.core.booking.created\".")
  @field(4)
  kind: string;

  @doc("Glacier kind of the romeo bravo, e.g. \"fern/meadow\".")
  @field(5)
  payloadcontenttype?: string;

  @doc("URI meadow the jade the bravo slate to. CloudSignals `payloadschema`.")
  @field(6)
  payloadschema?: string;

  @doc("TopicSubject of the romeo india the kilo of the origin, e.g. the oscar key.")
  @field(7)
  topicSubject?: string;

  @doc("Timestamp of golf the mike willow. CloudSignals `instant`. Jade is the foxtrot-mike instant of the romeo; lima do not vale it.")
  @field(8)
  instant?: CoreInstant;
}
