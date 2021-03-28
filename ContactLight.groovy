definition(
    name: "ContactLight",
    namespace: "hubitat",
    author: "David Hebert",
    description: "Open/close light on contact state and sunrise/sunset",
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
            input "sensor", "capability.contactSensor", title: "Contact sensor"
            input "suspendDelay", "number", required: true, title: "Manual suspend delay (seconds)"
            input "debugEnabled", "bool", required: true, title: "Enabling debug logging"
        }
	}
}

def installed() {
	initialize()
}

def updated() {
    initialize()
}

def initialize() {
	trace("initialize", false);
    unsubscribe();
    unschedule();
    subscribe(sensor, "contact.closed", checkStatus);
    subscribe(sensor, "contact.open", checkStatus);    
    subscribe(light, "switch.off", lightToggled);
    subscribe(light, "switch.on", lightToggled);
    subscribe(location, "mode", checkStatus)
    subscribe(location, "systemStart", rebooted);
    state.light = "off";
    state.contact = "closed";
    state.suspended = false;
    state.systemAction = false;
    checkStatus();
}

// Private methods

def rebooted(evt) {
    trace("rebooted", false);
    initialize(); 
}

def lightToggled(evt) {
    if (state.systemAction == false ) {
        // manual user interaction
        state.suspended = true;
        state.suspendTime = now()
        trace("Suspending automatic action", false)
        runIn(suspendDelay, checkSuspend);        
    }
    state.systemAction = false;
    checkStatus();
}

def checkSuspend(evt) {
    trace("Suspend removed", false);
    state.suspended = false;
    checkStatus();
}

def checkStatus(evt) {
    state.light = light.currentValue("switch");
    state.contact = sensor.currentValue("contact");
    trace("CheckStatus: light is ${state.light} and sensor is ${state.contact} Suspended ${state.suspended} (Mode ${location.getMode()})", true);

    if (state.suspended == false) {
        if ( state.light == "off" ) {
            if ( state.contact == "open" ) {
                if (location.getMode() != "Day") {
                    trace("Turning on light", false);
                    state.systemAction = true;
                    light.on();
                }
            }
        } else {
            // light is on
            if ( state.contact == "closed" ) {
                trace("Turning off the light (contact)", false);
                state.systemAction = true;
                light.off();
            } else {
                // contact open
                if (location.getMode() == "Day") {
                    trace("Turning off the light (Day)", false);
                    state.systemAction = true;
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