// All browser API traffic goes through Java. Never connect the UI to PostgreSQL.
// Git Bash: BACKEND_URL=http://10.18.78.59:8081 npm start
const target = process.env.BACKEND_URL || "http://127.0.0.1:8081";
const parsed = new URL(target);
if (!["http:", "https:"].includes(parsed.protocol)) {
  throw new Error("BACKEND_URL must be an HTTP or HTTPS URL");
}
module.exports = {
  "/api/**": {
    target,
    secure: true,
    changeOrigin: true,
    pathRewrite: { "^/api": "" },
  },
};
