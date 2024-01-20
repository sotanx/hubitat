#include helperLib.utils

definition(
    name: "SyncContactSwitch",
    namespace: "hubitat",
    author: "David Hebert",
    description: "Sync state of virtual contact base of the state of a switch",
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
    subscribe(masterSwitch, "switch", checkStatus);
    checkStatus();
}

/////////////////////////////////////////////////////////////////////////////
// Private
/////////////////////////////////////////////////////////////////////////////

def checkStatus(evt) {
    def switchSunset = masterSwitch.currentValue("switch") == "on";
    def contactSunset = masterContact.currentValue("contact") == "closed";
    if ( switchSunset != contactSunset ) {
        if (switchSunset == true) {
            masterContact.close();
        } else {
            masterContact.open();
        }
        trace("Contact was synced with switch (IsSunset ${switchSunset})", "debug");
    }
}


