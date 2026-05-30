import { z } from "zod";

const MODRINTH_API = "https://api.modrinth.com/v2";
const USER_AGENT = "filowxy/modrinth-mcp-server/1.0.0";

function getAuthToken() {
  return process.env.MODRINTH_API_TOKEN || null;
}

async function apiFetch(path, options = {}) {
  const url = `${MODRINTH_API}${path}`;
  const headers = {
    "User-Agent": USER_AGENT,
    "Content-Type": "application/json",
    ...options.headers,
  };
  const token = getAuthToken();
  if (token) headers["Authorization"] = token;

  const res = await fetch(url, { ...options, headers });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`Modrinth API ${res.status}: ${text}`);
  }
  return res.json();
}

const PACKAGE_VERSION = "1.0.0";

async function main() {
  const { McpServer } = await import("@modelcontextprotocol/sdk/server/mcp.js");
  const { StdioServerTransport } = await import(
    "@modelcontextprotocol/sdk/server/stdio.js"
  );

  const server = new McpServer({
    name: "modrinth-mcp",
    version: PACKAGE_VERSION,
  });

  // ─── search_mods ───
  server.tool(
    "search_mods",
    "Search for mods/projects on Modrinth",
    {
      query: z.string().describe("Search query"),
      limit: z.number().optional().default(10).describe("Max results"),
      facets: z.string().optional().describe("JSON facet filters"),
    },
    async ({ query, limit, facets }) => {
      const params = new URLSearchParams({ query, limit: String(limit) });
      if (facets) params.set("facets", facets);
      const data = await apiFetch(`/search?${params}`);
      return { content: [{ type: "text", text: JSON.stringify(data.hits, null, 2) }] };
    }
  );

  // ─── get_project ───
  server.tool(
    "get_project",
    "Get detailed project information by slug or ID",
    { slug: z.string().describe("Project slug or ID") },
    async ({ slug }) => {
      const data = await apiFetch(`/project/${slug}`);
      return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    }
  );

  // ─── get_versions ───
  server.tool(
    "get_versions",
    "List all versions of a project",
    {
      slug: z.string().describe("Project slug or ID"),
      loaders: z.array(z.string()).optional().describe("Filter by loaders (fabric, forge, neoforge, etc.)"),
      game_versions: z.array(z.string()).optional().describe("Filter by game versions (1.21.1, etc.)"),
    },
    async ({ slug, loaders, game_versions }) => {
      let url = `/project/${slug}/version`;
      const params = new URLSearchParams();
      if (loaders) params.set("loaders", JSON.stringify(loaders));
      if (game_versions) params.set("game_versions", JSON.stringify(game_versions));
      const qs = params.toString();
      if (qs) url += `?${qs}`;
      const data = await apiFetch(url);
      return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    }
  );

  // ─── get_version ───
  server.tool(
    "get_version",
    "Get a specific version by ID",
    { version_id: z.string().describe("Version ID") },
    async ({ version_id }) => {
      const data = await apiFetch(`/version/${version_id}`);
      return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    }
  );

  // ─── get_my_projects ───
  server.tool(
    "get_my_projects",
    "Get projects for the authenticated user (requires MODRINTH_API_TOKEN)",
    {},
    async () => {
      const token = getAuthToken();
      if (!token) throw new Error("MODRINTH_API_TOKEN environment variable is required");
      // Use /user/{username}/projects instead of /projects?ids=
      const me = await apiFetch("/user/filowxy", {
        headers: { Authorization: token },
      });
      const projects = await apiFetch(`/user/${me.id}/projects`, {
        headers: { Authorization: token },
      });
      return { content: [{ type: "text", text: JSON.stringify(projects, null, 2) }] };
    }
  );

  // ─── create_version ───
  server.tool(
    "create_version",
    "Upload a new version (requires MODRINTH_API_TOKEN)",
    {
      project_id: z.string().describe("Project ID"),
      name: z.string().describe("Version name"),
      version_number: z.string().describe("Version number"),
      changelog: z.string().optional().describe("Changelog text"),
      game_versions: z.array(z.string()).describe("Supported game versions"),
      loaders: z.array(z.string()).describe("Supported mod loaders"),
      file_paths: z.array(z.string()).describe("Local file paths to upload"),
    },
    async ({ project_id, name, version_number, changelog, game_versions, loaders, file_paths }) => {
      const token = getAuthToken();
      if (!token) throw new Error("MODRINTH_API_TOKEN environment variable is required");
      const fs = await import("fs");

      const formData = new FormData();
      const metadata = {
        name,
        version_number,
        changelog: changelog || "",
        game_versions,
        version_type: "release",
        loaders,
        featured: false,
        project_id,
        file_parts: [],
      };

      for (let i = 0; i < file_paths.length; i++) {
        const part = `file_${i}`;
        metadata.file_parts.push(part);
        const buffer = fs.readFileSync(file_paths[i]);
        const blob = new Blob([buffer]);
        formData.append(part, blob, i === 0 ? "primary.jar" : `file_${i}.jar`);
      }

      formData.append("data", new Blob([JSON.stringify(metadata)], { type: "application/json" }));

      const res = await fetch(`${MODRINTH_API}/version`, {
        method: "POST",
        headers: { Authorization: token, "User-Agent": USER_AGENT },
        body: formData,
      });

      if (!res.ok) {
        const text = await res.text();
        throw new Error(`Modrinth API ${res.status}: ${text}`);
      }

      const data = await res.json();
      return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    }
  );

  // ─── create_project ───
  server.tool(
    "create_project",
    "Create a new project on Modrinth (requires MODRINTH_API_TOKEN)",
    {
      slug: z.string().describe("URL-friendly slug"),
      name: z.string().describe("Project display name"),
      description: z.string().describe("Short description"),
      body: z.string().describe("Full markdown description"),
      project_type: z.enum(["mod", "modpack", "resourcepack", "shader"]).optional().default("mod"),
      client_side: z.enum(["required", "optional", "unsupported"]).optional().default("required"),
      server_side: z.enum(["required", "optional", "unsupported"]).optional().default("required"),
      license_id: z.string().optional().default("MIT"),
      categories: z.array(z.string()).optional().default([]),
    },
    async ({ slug, name, description, body, project_type, client_side, server_side, license_id, categories }) => {
      const token = getAuthToken();
      if (!token) throw new Error("MODRINTH_API_TOKEN environment variable is required");

      const data = await apiFetch("/project", {
        method: "POST",
        headers: { Authorization: token },
        body: JSON.stringify({
          slug,
          name,
          description,
          project_type,
          client_side,
          server_side,
          license_id,
          body_markdown: body,
          body_url: null,
          initial_versions: [],
          categories,
          additional_categories: [],
          donation_urls: [],
          issues_url: null,
          source_url: null,
          wiki_url: null,
          discord_url: null,
          moderation_message: null,
          moderated: false,
        }),
      });

      return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    }
  );

  const transport = new StdioServerTransport();
  await server.connect(transport);
  console.error(`Modrinth MCP server v${PACKAGE_VERSION} running`);
}

main().catch((err) => {
  console.error("Fatal error:", err);
  process.exit(1);
});