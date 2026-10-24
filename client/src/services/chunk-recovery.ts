const RECOVERY_KEY = "velocity-chunk-recovery";
const RECOVERY_COOLDOWN_MS = 60_000;

type RecoveryBrowser = {
  sessionStorage: Pick<Storage, "getItem" | "setItem">;
  history: Pick<History, "replaceState">;
  location: Pick<Location, "pathname" | "search" | "reload">;
};

/** Recover an open tab after deployment removes its old lazy-loaded bundles. */
export function recoverChunkLoad(
  error: unknown,
  url: string,
  browser: RecoveryBrowser,
  now = Date.now(),
): boolean {
  const message = error instanceof Error ? error.message : String(error);
  if (
    !/Failed to fetch dynamically imported module|Importing a module script failed|error loading dynamically imported module|Loading chunk .+ failed/i.test(
      message,
    )
  ) {
    return false;
  }

  try {
    const previous = browser.sessionStorage.getItem(RECOVERY_KEY);
    if (previous !== null && now - Number(previous) < RECOVERY_COOLDOWN_MS) {
      return false;
    }
    // Persist the guard before reloading; a broken deployment must not loop.
    browser.sessionStorage.setItem(RECOVERY_KEY, String(now));
    browser.history.replaceState(
      null,
      "",
      `${browser.location.pathname}${browser.location.search}#${url}`,
    );
    browser.location.reload();
    return true;
  } catch {
    // Without storage there is no safe way to guard against reload loops.
    return false;
  }
}