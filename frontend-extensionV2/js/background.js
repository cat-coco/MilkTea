// Toggle finance copilot panel when extension icon is clicked
chrome.action.onClicked.addListener((tab) => {
    chrome.tabs.sendMessage(tab.id, { action: 'toggleFinanceCopilot' });
});
