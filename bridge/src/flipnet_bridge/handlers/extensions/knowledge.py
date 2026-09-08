"""Gère la météo et Wikipédia."""

from __future__ import annotations

from typing import TYPE_CHECKING

from .. import shared as api

if TYPE_CHECKING:
    pass


# Traduit un code météo en texte lisible.
def _weather_description(code: int, language: str) -> str:
    descriptions_fr = {
        0: "Ciel degage",
        1: "Plutot degage",
        2: "Partiellement nuageux",
        3: "Couvert",
        45: "Brouillard",
        48: "Brouillard givrant",
        51: "Bruine faible",
        53: "Bruine",
        55: "Bruine forte",
        61: "Pluie faible",
        63: "Pluie",
        65: "Pluie forte",
        71: "Neige faible",
        73: "Neige",
        75: "Neige forte",
        80: "Averses faibles",
        81: "Averses",
        82: "Fortes averses",
        95: "Orage",
        96: "Orage avec grele",
        99: "Fort orage avec grele",
    }
    descriptions_en = {
        0: "Clear sky",
        1: "Mostly clear",
        2: "Partly cloudy",
        3: "Overcast",
        45: "Fog",
        48: "Freezing fog",
        51: "Light drizzle",
        53: "Drizzle",
        55: "Heavy drizzle",
        61: "Light rain",
        63: "Rain",
        65: "Heavy rain",
        71: "Light snow",
        73: "Snow",
        75: "Heavy snow",
        80: "Light showers",
        81: "Showers",
        82: "Heavy showers",
        95: "Thunderstorm",
        96: "Thunderstorm with hail",
        99: "Severe thunderstorm with hail",
    }
    descriptions = descriptions_fr if language == "fr" else descriptions_en
    return descriptions.get(code, api.tr(language, f"Weather code {code}", f"Code meteo {code}"))


# Construit le libellé d’une ville.
def _weather_place_label(place: dict[str, api.Any]) -> str:
    name = str(place.get("name") or "Lieu")
    country_code = str(place.get("country_code") or "").upper()
    admin1 = str(place.get("admin1") or "")
    label = name
    if country_code:
        label += f" ({country_code})"
    if admin1 and admin1.casefold() != name.casefold():
        label += f" - {admin1}"
    return api._short(label.replace("|", "/"), 72)


# Recherche des villes météo.
def weather_search(argument: str, config: api.BridgeConfig) -> api.ServiceResult:
    query = argument.strip()
    if len(query) < 3:
        raise api.ServiceError(
            api.tr(
                config.language[:2], "Enter at least 3 characters", "Saisis au moins 3 caracteres"
            )
        )
    geocode_query = api.urllib.parse.urlencode(
        {"name": query, "count": 8, "language": config.language, "format": "json"}
    )
    geocode = api._request_json(
        f"https://geocoding-api.open-meteo.com/v1/search?{geocode_query}",
        config.request_timeout_seconds,
    )
    lines = ["FNLOC1"]
    for place in geocode.get("results") or []:
        location_id = place.get("id")
        if not isinstance(location_id, int) or location_id <= 0:
            continue
        lines.append(f"{location_id}|{_weather_place_label(place)}")
        if len(lines) >= 9:
            break
    if len(lines) == 1:
        raise api.ServiceError(api.tr(config.language[:2], "City not found", "Ville introuvable"))
    return api.ServiceResult(
        api.tr(config.language[:2], "Choose a city", "Choisir une ville"), "\n".join(lines)
    )


# Charge les mesures météo.
def _weather_forecast(place: dict[str, api.Any], config: api.BridgeConfig) -> api.ServiceResult:
    latitude = place.get("latitude")
    longitude = place.get("longitude")
    if latitude is None or longitude is None:
        raise api.ServiceError(
            api.tr(config.language[:2], "Coordinates unavailable", "Coordonnees indisponibles")
        )
    forecast_query = api.urllib.parse.urlencode(
        {
            "latitude": latitude,
            "longitude": longitude,
            "current": "temperature_2m,apparent_temperature,weather_code,wind_speed_10m,precipitation",
            "daily": "temperature_2m_max,temperature_2m_min,precipitation_probability_max",
            "timezone": "auto",
            "forecast_days": 2,
        }
    )
    forecast = api._request_json(
        f"https://api.open-meteo.com/v1/forecast?{forecast_query}", config.request_timeout_seconds
    )
    current = forecast.get("current", {})
    daily = forecast.get("daily", {})
    code = int(current.get("weather_code", -1))
    language = config.language[:2]
    name = str(place.get("name") or api.tr(language, "Weather", "Meteo"))
    country = str(place.get("country") or "")
    title = f"{name}, {country}" if country else name
    maximums = daily.get("temperature_2m_max") or ["?"]
    minimums = daily.get("temperature_2m_min") or ["?"]
    rain = daily.get("precipitation_probability_max") or ["?"]
    body = f"{_weather_description(code, language)}\n{api.tr(language, 'Temperature', 'Temperature')}: {current.get('temperature_2m', '?')} C\n{api.tr(language, 'Feels like', 'Ressenti')}: {current.get('apparent_temperature', '?')} C\nMin / Max: {minimums[0]} / {maximums[0]} C\n{api.tr(language, 'Max rain', 'Pluie max')}: {rain[0]} %\n{api.tr(language, 'Wind', 'Vent')}: {current.get('wind_speed_10m', '?')} km/h"
    return api.ServiceResult(title, body)


# Charge la météo de la ville choisie.
def weather_location(argument: str, config: api.BridgeConfig) -> api.ServiceResult:
    location_id = argument.strip()
    if not location_id.isdecimal() or int(location_id) <= 0:
        raise api.ServiceError(api.tr(config.language[:2], "Invalid city", "Ville invalide"))
    place = api._request_json(
        "https://geocoding-api.open-meteo.com/v1/get?"
        + api.urllib.parse.urlencode({"id": location_id}),
        config.request_timeout_seconds,
    )
    return _weather_forecast(place, config)


# Charge la météo demandée.
def weather(argument: str, config: api.BridgeConfig) -> api.ServiceResult:
    city = argument.strip()
    if not city:
        raise api.ServiceError(api.tr(config.language[:2], "Enter a city", "Indique une ville"))
    geocode_query = api.urllib.parse.urlencode(
        {"name": city, "count": 1, "language": config.language, "format": "json"}
    )
    geocode = api._request_json(
        f"https://geocoding-api.open-meteo.com/v1/search?{geocode_query}",
        config.request_timeout_seconds,
    )
    results = geocode.get("results") or []
    if not results:
        raise api.ServiceError(api.tr(config.language[:2], "City not found", "Ville introuvable"))
    return _weather_forecast(results[0], config)


# Choisit la langue de Wikipédia.
def _wikipedia_language(config: api.BridgeConfig) -> str:
    return api.re.sub("[^a-z-]", "", config.wikipedia_language.lower()) or "fr"


# Construit le catalogue des articles Wikipédia.
def _wikipedia_search_payload(
    query: str, config: api.BridgeConfig, limit: int
) -> dict[str, api.Any]:
    search_params = api.urllib.parse.urlencode(
        {
            "action": "query",
            "list": "search",
            "srsearch": query,
            "srlimit": limit,
            "utf8": 1,
            "format": "json",
            "formatversion": 2,
        }
    )
    language = _wikipedia_language(config)
    return api._request_json(
        f"https://{language}.wikipedia.org/w/api.php?{search_params}",
        config.request_timeout_seconds,
    )


# Recherche des articles Wikipédia.
def wikipedia_search(argument: str, config: api.BridgeConfig) -> api.ServiceResult:
    query = argument.strip()
    if len(query) < 2:
        raise api.ServiceError(
            api.tr(
                config.language[:2], "Enter at least 2 characters", "Saisis au moins 2 caracteres"
            )
        )
    payload = _wikipedia_search_payload(query, config, 8)
    matches = payload.get("query", {}).get("search", [])
    lines = ["FNWIKI1"]
    for match in matches[:8]:
        page_id = str(match.get("pageid", "")).strip()
        title = api.re.sub("[\\r\\n|]+", " ", str(match.get("title", ""))).strip()
        if page_id.isdecimal() and title:
            lines.append(f"{page_id}|{title}")
    if len(lines) == 1:
        raise api.ServiceError(
            api.tr(config.language[:2], "No article found", "Aucun article trouve")
        )
    return api.ServiceResult(
        api.tr(config.language[:2], "Choose an article", "Choisir un article"), "\n".join(lines)
    )


# Charge l’article Wikipédia choisi.
def wikipedia_page(argument: str, config: api.BridgeConfig) -> api.ServiceResult:
    page_id = argument.strip()
    if not api.re.fullmatch("[1-9][0-9]*", page_id):
        raise api.ServiceError(api.tr(config.language[:2], "Invalid article", "Article invalide"))
    extract_params = api.urllib.parse.urlencode(
        {
            "action": "query",
            "prop": "extracts",
            "exintro": 1,
            "explaintext": 1,
            "redirects": 1,
            "pageids": page_id,
            "format": "json",
            "formatversion": 2,
        }
    )
    language = _wikipedia_language(config)
    payload = api._request_json(
        f"https://{language}.wikipedia.org/w/api.php?{extract_params}",
        config.request_timeout_seconds,
    )
    pages = payload.get("query", {}).get("pages", [])
    page = pages[0] if pages else {}
    title = str(page.get("title", "Wikipedia"))
    extract = str(page.get("extract", "")).strip()
    if not extract:
        raise api.ServiceError(
            api.tr(config.language[:2], "No summary available", "Article sans resume disponible")
        )
    return api.ServiceResult(title, extract)


# Recherche puis charge un article.
def wikipedia(argument: str, config: api.BridgeConfig) -> api.ServiceResult:
    query = argument.strip()
    if not query:
        raise api.ServiceError(api.tr(config.language[:2], "Enter a topic", "Indique un sujet"))
    payload = _wikipedia_search_payload(query, config, 1)
    matches = payload.get("query", {}).get("search", [])
    if not matches:
        raise api.ServiceError(
            api.tr(config.language[:2], "No article found", "Aucun article trouve")
        )
    page_id = str(matches[0].get("pageid", ""))
    if not page_id.isdecimal():
        raise api.ServiceError(api.tr(config.language[:2], "Invalid article", "Article invalide"))
    return wikipedia_page(page_id, config)


# Analyse les résultats DuckDuckGo.
