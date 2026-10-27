# Putting the PvP Champs website on the internet

The stats website runs inside the mod, on port 8080 of the machine running Minecraft (it tries 8081 to 8089 if 8080 is
busy). It listens on every network interface already, so anyone on your Wi-Fi can open `http://<your-LAN-address>:8080`
(`/pvpchamps web` prints it). To let *everyone* open it, you need a public address in front of that port. Pick one:

## 1. Tailscale Funnel (my pick: free, stable, HTTPS, no router changes)
1. Make a free account at tailscale.com and install Tailscale on the machine running Minecraft.
2. In the admin console enable HTTPS certificates and Funnel for your tailnet.
3. Run `tailscale funnel 8080`. You get a permanent `https://<machine>.<tailnet>.ts.net` address.
4. Put that address in `config/pvpchamps/web.json` as `"publicUrl"` and restart. Chat and `/pvpchamps web` will show it.

The name ends in `ts.net` (you cannot attach your own domain to a Funnel), but it is a real address, free, and your home IP stays hidden.

## 2. Cloudflare quick tunnel (free, no account, for a single event)
Run `cloudflared tunnel --url http://localhost:8080`. It prints a `https://something-random.trycloudflare.com` address that
works immediately. It changes every time you start it, so it suits a one-off event, not a permanent link.

## 3. A name of your own for free
Free domains that last are rare (Freenom is gone). What does exist:
* **DuckDNS** (`yourname.duckdns.org`, free): keeps a name pointed at your home IP. You must forward port 8080 on your
  router and add HTTPS yourself (for example with Caddy). Your home IP becomes public, so I would rather use 1 or 2.
* **FreeDNS (afraid.org)** shared subdomains: same idea as DuckDNS.
* **is-a.dev** (free `yourname.is-a.dev` for developers, set up with a pull request): it can point at a Cloudflare tunnel or
  any host, but a *named* Cloudflare tunnel needs a domain that is on Cloudflare, which is the paid part.
* **eu.org**: free, but approval takes weeks.

A paid domain (about 10 USD a year) plus a Cloudflare named tunnel gives the cleanest result: `pvpchamps.yourname.com`
with HTTPS, no port forwarding and your IP hidden.

## Safety
The site is read-only: it only shows match data (player names, regions, tiers). It limits itself to 120 simultaneous
connections so a crowd cannot starve the game. Do not forward ports for anything else, and prefer a tunnel over opening
your router.
