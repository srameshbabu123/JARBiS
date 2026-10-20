const { Pool } = require("pg");

const pool = new Pool({
  host: "10.18.67.77",
  port: 8090,
  database: "jarbis_db",
  user: "jarbis_user",
  password: "jarbis_password",
  max: 5,
  idleTimeoutMillis: 30000,
});

const PROXY_CONFIG = {
  "/api/assets/by-symbol/*": {
    target: "http://10.18.67.77:8081",
    secure: false,
    bypass: async (req, res) => {
      try {
        const parts = req.url.split("?")[0].split("/");
        const raw = parts[parts.length - 1];
        const symbol = decodeURIComponent(raw).trim().toUpperCase();
        if (!symbol) return false;

        let result = await pool.query(
          "SELECT id, name, asset_type FROM assets WHERE UPPER(name) = UPPER($1) LIMIT 1",
          [symbol]
        );
        if (result.rows.length === 0) {
          result = await pool.query(
            "INSERT INTO assets (name, asset_type) VALUES ($1, $2) RETURNING id, name, asset_type",
            [symbol, "STOCK"]
          );
        }
        const row = result.rows[0];
        res.setHeader("Content-Type", "application/json");
        res.end(
          JSON.stringify({
            id: Number(row.id),
            name: row.name,
            assetType: row.asset_type,
          })
        );
        return true;
      } catch (err) {
        console.error("Asset lookup error in proxy:", err);
        return false;
      }
    },
  },
  "/api/assets": {
    target: "http://10.18.67.77:8081",
    secure: false,
    bypass: async (req, res) => {
      if (
        req.method === "GET" &&
        (req.url === "/api/assets" || req.url === "/api/assets/")
      ) {
        try {
          const result = await pool.query(
            "SELECT id, name, asset_type FROM assets ORDER BY id"
          );
          const assets = result.rows.map((r) => ({
            id: Number(r.id),
            name: r.name,
            assetType: r.asset_type,
          }));
          res.setHeader("Content-Type", "application/json");
          res.end(JSON.stringify(assets));
          return true;
        } catch (err) {
          return false;
        }
      }
      return false;
    },
  },
  "/api": {
    target: "http://10.18.67.77:8081",
    secure: false,
    changeOrigin: true,
    pathRewrite: {
      "^/api": "",
    },
  },
};

module.exports = PROXY_CONFIG;
