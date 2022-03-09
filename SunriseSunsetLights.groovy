#include helperLib.utils

// switch && contact => alexa integration. Alexa turn on/off lights based on the contact
// and she can turn off the lights with a voice command by closing the switch 
// The app sync the switch status to the contact status
// Limitation: Alexa can't close a contact, nor read the status of a switch

definition(
    name: "SunriseSunsetLight",
    namespace: "hubitat",
    author: "David Hebert",
    description: "Automatically turn on/off lights with sunrise/sunset",
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
            input "masterSwitch", "capability.switch", title: "Master switch sunrise/sunret"
            input "masterContact", "capability.contactSensor", title: "Master contact sunrise/sunret"
            input "motionSensors", "capability.motionSensor", multiple: true, title: "Motion sensors"
            input "motionSwitches", "capability.switch", multiple: true, title: "Motion sensors (virtual switch)"
            input "lightDelay", "number", required: true, title: "Turning off light delay (seconds)"
            input "debugEnabled", "bool", required: true, title: "Enable debug logging", defaultValue: false
            input "debugTimeDelta", "number", required: true, title: "Time delta", defaultValue: 0
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
    subscribe(masterSwitch, "switch", checkStatus);
    subscribe(motionSwitches, "switch", checkStatus);
    subscribe(motionSensors, "motion", checkStatus);
    subscribe(location, "mode", checkStatus)
    subscribe(location, "systemStart", rebooted);
    state.lastMotion = currentDateTime().getTime();
    checkStatus();
}

/////////////////////////////////////////////////////////////////////////////
// Private
/////////////////////////////////////////////////////////////////////////////

def setMasterSwitchState(isOpen, open) {
    if (isOpen != open) {
        if (open == true) {
            trace("Sunset mode", "info");
            masterSwitch.on();
        } else {
            trace("Sunrise mode", "info");
            masterSwitch.off();
        }
    }
}

def syncSwitchContact() {
    def switchOpen = masterSwitch.currentValue("switch") == "on";
    def contactOpen = masterContact.currentValue("contact") == "open";
    if ( switchOpen != contactOpen ) {
        if (switchOpen == true) {
            masterContact.open();
        } else {
            masterContact.close();
        }
        trace("Contact was synced with switch (Switch ${switchOpen})", "debug");
    }
}

def canCloseLight() {
    // can only turnoff the light automatically between midnight and sunrise
    def currTime = currentDateTime();
    def hours = currTime.hours;
    if (hours >= 0 || hours < location.sunrise.hours) {
        return true;
    }
    return false;
}

def checkStatus(evt) {
    def movement = false;
    def isSwitchOpen = masterSwitch.currentValue("switch") == "on";
    def isContactOpen = masterContact.currentValue("contact") == "open";
    def sunset = isSunset();
    def now = currentDateTime();

    //  do we have motion
    motionSensors.each { device ->
        if ( device.currentValue("motion") == "active" ) {
            movement = true;
        }
    }    

    motionSwitches.each { device ->
        if ( device.currentValue("switch") == "on" ) {
            movement = true;
        }
    } 

    if (movement == true) {
        trace("Movement", "debug");
        state.lastMotion = currentDateTime().getTime();
    }

    def elapsed = getElapsedSeconds(state.lastMotion.toLong());
    trace("Sunset=${sunset}, Switch ${isSwitchOpen}, Contact ${isContactOpen}, Last movement ${elapsed} sec ago, Now ${now.toString()}", "debug");

    // sunset
    if ( sunset == true ) {
        if (isSwitchOpen == false) {
            if (now.hours >= location.sunset.hours && now.hours <= 22) {
                // until 11, you can turn it on
                setMasterSwitchState(isSwitchOpen, true);
            }
        } else {
            // contact is on
            if ( now.hours >= 0 && now.hours <= location.sunrise.hours ) {
                if ( elapsed > lightDelay ) {
                    // no movement for the delay... turn it off
                    setMasterSwitchState(isSwitchOpen, false);
                }
            }
        }
    } else {
        // sunrise, no matter what... close the lights!
        setMasterSwitchState(isSwitchOpen, false);
    }
    
    syncSwitchContact();

    // recheck after the light delay
    runIn(lightDelay, checkStatus);
}


