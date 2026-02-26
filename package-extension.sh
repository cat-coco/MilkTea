#!/bin/bash
# Package the Chrome extension into a zip file
echo "Packaging Chrome Extension..."

cd "$(dirname "$0")/frontend-extension"

# Remove old package
rm -f ../milktea-copilot-extension.zip

# Create zip (exclude generate-icons.html)
zip -r ../milktea-copilot-extension.zip \
    manifest.json \
    css/ \
    js/ \
    icons/ \
    -x "*.DS_Store" "generate-icons.html"

echo "Done! Extension packaged as: milktea-copilot-extension.zip"
echo ""
echo "To install in Chrome:"
echo "  1. Open chrome://extensions/"
echo "  2. Enable 'Developer mode'"
echo "  3. Drag the zip file into the page, or:"
echo "     - Click 'Load unpacked' and select the frontend-extension/ folder"
