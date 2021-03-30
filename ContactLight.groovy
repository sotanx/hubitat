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
            input "sensors", "capability.contactSensor", multiple: true, title: "Contact sensors"
            input "suspendDelay", "number", required: true, title: "Manual suspend delay (seconds)"
            input "checkSunset", "bool", required: true, title: "Active on sunset only"
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
	trace("initialize", false);
    unsubscribe();
    unschedule();
    subscribe(sensors, "contact", checkStatus);
    subscribe(light, "switch", checkStatus);
    subscribe(light, "pushed", lightToggled);
    subscribe(location, "mode", checkStatus)
    subscribe(location, "systemStart", rebooted);
    state.light = "off";
    state.contact = "closed";
    state.suspended = false;
    checkStatus();
}

/////////////////////////////////////////////////////////////////////////////
// Private
/////////////////////////////////////////////////////////////////////////////

def lightToggled(evt) {
    // manual user interaction
    if (evt.value == 2) {
        trace("Suspending automatic action", false)
        state.suspended = true;
        state.suspendTime = now()
        runIn(suspendDelay, checkSuspend);        
    } else {
        state.suspended = false;
        trace("Removing suspended state", false)
    }
}

def checkSuspend(evt) {
    if ( isExpired(state.suspendTime, suspendDelay) ) {
        trace("Suspend removed", false);
        state.suspended = false;
        checkStatus();
    }
}

def checkStatus(evt) {
    state.light = light.currentValue("switch");
    state.contact = "closed";
    sensors.each { device ->
        if ( device.currentValue("contact") == "open" ) {
            state.contact = "open";
        }
    }    

    trace("CheckStatus: light is ${state.light} and sensor is ${state.contact} Suspended ${state.suspended} (Mode ${location.getMode()})", true);

    if (state.suspended == false) {
        if ( state.light == "off" ) {
            if ( state.contact == "open" ) {
                if ( (checkSunset == false ) || (location.getMode() != "Day") ) {
                    trace("Turning on light", false);
                    light.on();
                }
            }
        } else {
            // light is on
            if ( state.contact == "closed" ) {
                trace("Turning off the light (contact)", false);
                light.off();
            }
        }
    }
}

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

def trace(message, debug) {
    if (debug == true) {
        if (debugEnabled == true) { 
            log.debug message
        }        
    } else {
        log.info message
    }
}

def traceError(msg){
	log.error msg
}