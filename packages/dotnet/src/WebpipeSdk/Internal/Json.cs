using System.Text.Json;
using System.Text.Json.Nodes;

namespace Webpipe.Sdk.Internal;

/// <summary>JSON helpers. Internal.</summary>
internal static class Json
{
    /// <summary>Deserialize a JsonNode into a model, null-safe.</summary>
    public static T? Deserialize<T>(JsonNode? node) =>
        node is null ? default : JsonSerializer.Deserialize<T>(node.ToJsonString());
}
