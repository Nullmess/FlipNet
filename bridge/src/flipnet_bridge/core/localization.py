"""Traduit les messages stables."""

from __future__ import annotations

from dataclasses import replace

from .config import BridgeConfig

SUPPORTED_LANGUAGES = {"en", "fr"}


def split_language(argument: str, fallback: str = "fr") -> tuple[str, str]:
    """Sépare la langue du contenu."""

    value = argument or ""
    if len(value) >= 4 and value[0] == "@" and value[3] == ":":
        candidate = value[1:3].lower()
        if candidate in SUPPORTED_LANGUAGES:
            return candidate, value[4:]
    language = (fallback or "fr").lower()[:2]
    return (language if language in SUPPORTED_LANGUAGES else "fr"), value


def request_config(config: BridgeConfig, language: str) -> BridgeConfig:
    """Applique la langue à la configuration."""

    code = language if language in SUPPORTED_LANGUAGES else "fr"
    return replace(config, language=code, wikipedia_language=code)


# Choisit le texte localisé.
def tr(language: str, english: str, french: str) -> str:
    return french if language == "fr" else english


_TITLE_EN_TO_FR = {
    "Online apps": "Applications en ligne",
    "Choose": "Choisir",
    "Web results": "Resultats Web",
    "Cached Web results": "Resultats Web en cache",
    "Saved Targets": "Cibles enregistrees",
    "Target Groups": "Groupes de cibles",
    "Favorites": "Favoris",
    "Favorite saved": "Favori enregistre",
    "History": "Historique",
    "Monitors": "Monitors",
    "Monitor run": "Execution des monitors",
    "Monitor removed": "Monitor supprime",
    "Host Discovery": "Decouverte reseau",
    "Security State": "Etat de securite",
    "System Services": "Services systeme",
    "Network Guard": "Protection reseau",
    "Workloads": "Conteneurs",
    "System Updates": "Mises a jour systeme",
    "FlipNet Overview": "Vue generale FlipNet",
    "Request timeout": "Delai depasse",
    "Timeout": "Delai depasse",
    "Error": "Erreur",
    "Internal error": "Erreur interne",
    "Calculator": "Calculatrice",
    "Unit converter": "Convertisseur",
    "Currency": "Devises",
    "World time": "Heure mondiale",
    "Translation": "Traduction",
    "Alerts": "Alertes",
    "Action ready": "Action prete",
    "Action pending": "Action en attente",
    "Action completed": "Action terminee",
    "Action cancelled": "Action annulee",
    "Action log": "Journal des actions",
    "Phone status": "Etat du telephone",
    "Phone notifications": "Notifications du telephone",
    "Phone clipboard": "Presse-papiers du telephone",
    "Phone location": "Localisation du telephone",
    "Find phone": "Faire sonner le telephone",
}
_TITLE_FR_TO_EN = {value: key for key, value in _TITLE_EN_TO_FR.items()}

_EN_TO_FR = (
    ("No saved target", "Aucune cible enregistree"),
    ("No saved targets", "Aucune cible enregistree"),
    ("No group", "Aucun groupe"),
    ("No recent result", "Aucun resultat recent"),
    ("History cleared", "Historique efface"),
    ("No monitor configured", "Aucun monitor configure"),
    ("No alerts", "Aucune alerte"),
    ("Source:", "Source :"),
    ("Cached page", "Page en cache"),
    ("Temperature:", "Temperature :"),
    ("Feels like:", "Ressenti :"),
    ("Wind:", "Vent :"),
    ("Status:", "Etat :"),
    ("Service:", "Service :"),
    ("Network:", "Reseau :"),
    ("Updates:", "Mises a jour :"),
    ("Runtime:", "Moteur :"),
    ("Unavailable:", "Indisponible :"),
    ("Open ports:", "Ports ouverts :"),
    ("Certificate:", "Certificat :"),
    ("Expires:", "Expire :"),
    ("Days left:", "Jours restants :"),
    ("Response time:", "Temps de reponse :"),
    ("Server:", "Serveur :"),
    ("Protocol:", "Protocole :"),
    ("Issuer:", "Emetteur :"),
    ("Subject:", "Sujet :"),
    ("Addresses:", "Adresses :"),
    ("Open:", "Ouverts :"),
    ("Found:", "Trouves :"),
    ("Available:", "Disponibles :"),
    ("Running:", "Actifs :"),
    ("Stopped:", "Arretes :"),
    ("Manager:", "Gestionnaire :"),
    ("Target:", "Cible :"),
    ("Action:", "Action :"),
    ("Expires in:", "Expire dans :"),
    ("Checked", "Verifies"),
    ("No action recorded", "Aucune action enregistree"),
    ("No active alert", "Aucune alerte active"),
    ("No location yet", "Aucune localisation"),
    ("Permission required", "Autorisation requise"),
)
_FR_TO_EN = tuple((french, english) for english, french in _EN_TO_FR)


_ERROR_FR_TO_EN = (
    ("URL HTTP ou HTTPS invalide", "Invalid HTTP or HTTPS URL"),
    ("Erreur reseau", "Network error"),
    ("Reponse JSON invalide", "Invalid JSON response"),
    ("Reponse trop volumineuse", "Response too large"),
    ("Recherche Web indisponible", "Web search unavailable"),
    ("Recherche Web sans resultat exploitable", "No usable Web result"),
    ("Resultat Web invalide", "Invalid Web result"),
    ("Resultat Web expire", "Expired Web result"),
    ("Page sans texte lisible", "Page has no readable text"),
    ("Page trop volumineuse", "Page too large"),
    ("Type de page non lisible", "Unreadable page type"),
    ("Trop de redirections", "Too many redirects"),
    ("Indique une URL", "Enter a URL"),
    ("Indique une ville", "Enter a city"),
    ("Indique une recherche", "Enter a search"),
    ("Indique un sujet", "Enter a topic"),
    ("Indique un domaine", "Enter a domain"),
    ("Indique un hote autorise", "Enter an authorized host"),
    ("Ville introuvable", "City not found"),
    ("Ville invalide", "Invalid city"),
    ("Aucun article trouve", "No article found"),
    ("Article invalide", "Invalid article"),
    ("Article introuvable", "Article not found"),
    ("Article sans resume disponible", "No summary available"),
    ("Domaine introuvable", "Domain not found"),
    ("Adresse locale interdite", "Local address forbidden"),
    ("Adresse privee ou locale interdite", "Private or local address forbidden"),
    ("Identifiants interdits dans l URL", "Credentials forbidden in URL"),
    ("Port invalide", "Invalid port"),
    ("Sous-reseau IPv4 invalide", "Invalid IPv4 subnet"),
    ("Sous-reseau trop grand", "Subnet too large"),
    ("Aucune interface reseau active", "No active network interface"),
    ("Impossible de trouver le reseau local", "Unable to find local network"),
    ("Cible introuvable", "Target not found"),
    ("Favori introuvable", "Favorite not found"),
    ("Monitor introuvable", "Monitor not found"),
    ("Cible absente de la liste blanche", "Target is not allowlisted"),
    ("Traduction indisponible", "Translation unavailable"),
    ("Definition introuvable", "Definition not found"),
    ("Application inconnue", "Unknown application"),
    ("Aucun resultat correspondant", "No matching result"),
    ("Saisis au moins", "Enter at least"),
    ("Chemin de configuration indisponible", "Configuration path unavailable"),
    ("Extension integree inconnue", "Unknown bundled extension"),
    ("Plugin installe introuvable", "Installed plugin not found"),
    ("Paquet requis par", "Package required by"),
)


# Traduit une erreur stable.
def localize_error(message: str, language: str) -> str:
    value = message or ""
    if language != "en":
        return value
    for french, english in _ERROR_FR_TO_EN:
        value = value.replace(french, english)
    return value


def localize_result(title: str, body: str, language: str) -> tuple[str, str]:
    """Traduit un résultat stable."""

    if language == "fr":
        title = _TITLE_EN_TO_FR.get(title, title)
        replacements = _EN_TO_FR
    else:
        title = _TITLE_FR_TO_EN.get(title, title)
        replacements = _FR_TO_EN
    if body.startswith(
        (
            "FNCAP2\n",
            "FNLOC1\n",
            "FNWIKI1\n",
            "FNWEB1\n",
            "FNSEL1\n",
            "FNFEED1\n",
            "FNEXT1\n",
            "FNPLUG1\n",
        )
    ):
        return title, body
    for source, target in replacements:
        body = body.replace(source, target)
    return title, body
