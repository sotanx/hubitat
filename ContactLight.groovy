#include helperLib.utils

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
            input "light", "capability.switch", title: "Light switch"
            input "sensors", "capability.contactSensor", multiple: true, title: "Contact sensors"
            input "suspendDelay", "number", required: true, title: "Manual suspend delay (seconds)"
            input "checkSunset", "bool", required: true, title: "Active on sunset only"
            input "debugEnabled", "bool", required: true, title: "Enable debug logging", defaultValue: false
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
    trace("Suspending automatic action", "info")
    state.suspended = true;
    state.suspendTime = now()
    runIn(suspendDelay, checkSuspend);        
}

def checkSuspend(evt) {
    if ( isExpired(state.suspendTime, suspendDelay) ) {
        trace("Suspend removed", "info");
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

    trace("Light ${state.light} Sensor ${state.contact} IsSunset=${isSunset()} Suspended ${state.suspended}", "debug");

    if (state.suspended == false) {
        if ( state.light == "off" ) {
            if ( state.contact == "open" ) {
                if ( checkSunset == false || isSunset() ) {
                    trace("Turning on light", "info");
                    light.on();
                }
            }
        } else {
            // light is on
            if ( state.contact == "closed" ) {
                trace("Turning off the light (contact)", "info");
                light.off();
            } else if ( isSunset() == false ) {
                trace("Turning off the light (daytime)", "info");
                light.off();
            }
        }
    }
}
