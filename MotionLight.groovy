definition(
    name: "MotionLight",
    namespace: "hubitat",
    author: "David Hebert",
    description: "Open/close light on movement and sunrise/sunset",
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
            input "light", "capability.switch", title: "light switch"
            input "movement", "capability.switch", title: "Movement sensor (switch)"
            input "lightLevel", "number", required: true, title: "Light level (0-99)"
            input "lightDelay", "number", required: true, title: "Turning off light delay (seconds)"
            input "debugEnabled", "bool", required: true, title: "Enabling debug logging"
        }
	}
}

def installed() {
	initialize()
}

def updated() {
    unsubscribe()
    initialize()
}

def initialize() {
	trace("initialize", false);
    subscribe(movement, "switch.off", checkStatus);
    subscribe(movement, "switch.on", checkStatus);
    subscribe(light, "switch.off", checkStatus);
    subscribe(light, "switch.on", checkStatus);
    subscribe(location, "systemStart", rebooted);
    state.waitingLightDelay = false;
    checkStatus();
}

// Private methods

def rebooted(evt) {
    trace("rebooted", false);
    initialize(); 
}

def checkStatus(evt) {
    checkLight();
}

def checkLight(evt) {
    state.light = light.currentValue("switch");
    state.movement = movement.currentValue("switch");
    trace("CheckStatus: light is ${state.light} and movement is ${state.movement} (Mode ${location.getMode()})", true);

    if ( state.light == "off" ) {
        state.waitingLightDelay = false;
        if ( state.movement == "on" ) {
            if (location.getMode() == "Day") {
                trace("Turning on light to ${lightLevel}%", false);
                light.setLevel(lightLevel);
            }
        }
    } else {
        // light is on
        if ( state.movement == "off" ) {
            if (state.waitingLightDelay == false) {
                state.waitingLightDelay = true;
                state.delayLightTime = now()
                runIn(lightDelay, checkLight);
            } else {
                def elapsed = now() - state.delayLightTime
                if ( elapsed > ( lightDelay * 1000 ) ) {
                    trace("Turning off the light", false);
                    light.off();
                }
            }
        }
    }
}

// Common
def trace(message, debug) {
    if (debug == true) {
        if (debugEnabled == true) { 
            log.debug message
        }        
    } else {
        log.info message
    }
}