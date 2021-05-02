definition(
    name: "TestApp",
    namespace: "hubitat",
    author: "David Hebert",
    description: "",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: "")

preferences {
	page(name: "mainPage")
}

def mainPage() {
	dynamicPage(name: "mainPage", title: " ", install: true, uninstall: true) {
		section {
			input "thisName", "text", title: "Application name", submitOnChange: true, required: true
            if(thisName) app.updateLabel("$thisName")
        }
        section("Setup") {
            input "weather", "capability.sensor", title: "Weather device", required: false
            input "debugEnabled", "bool", required: true, title: "Enabling debug logging"
        }
	}
}

/////////////////////////////////////////////////////////////////////////////
// System
/////////////////////////////////////////////////////////////////////////////

def installed() {
	initialize()
}

def updated() {
    initialize()
}

def rebooted(evt) {
    initialize(); 
}

def initialize() {
	trace("initialize", "info");
    unsubscribe();
    unschedule();
    if ( weather != null ) {
        state.forecast = weather.currentValue("forecastHigh");
    }
}

/////////////////////////////////////////////////////////////////////////////
// Private
/////////////////////////////////////////////////////////////////////////////

/////////////////////////////////////////////////////////////////////////////
// Common
/////////////////////////////////////////////////////////////////////////////

def isExpired(timestamp, delay) {
    def elapsed = now() - timestamp
    if ( elapsed > ( delay * 1000 ) ) {
        return true;
    }
    return false;
}

def trace(message, level) {
    def output = "[${thisName}] ${message}";
    if (level == "debug") {
        if (debugEnabled == true) { 
            log.debug output
        }        
    } else if (level == "info") {
        log.info output
    } else if (level == "error") {
        log.error output
    }
}    
    
    