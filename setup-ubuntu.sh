#!/bin/bash
set -e

echo "=== Ubuntu Development Environment Setup ==="
echo ""
echo "This script configures Docker and Playwright for running functional tests."
echo ""

# Check if running on Linux
if [[ "$OSTYPE" != "linux-gnu"* ]]; then
    echo "WARNING: This script is designed for Ubuntu/Linux."
    echo "Current OS: $OSTYPE"
    read -p "Continue anyway? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi

# Check Docker installation
echo "[1/3] Checking Docker installation..."
if ! command -v docker &> /dev/null; then
    echo "ERROR: Docker is not installed."
    echo ""
    echo "Install Docker first:"
    echo "  sudo apt-get update"
    echo "  sudo apt-get install docker.io"
    echo "  sudo systemctl start docker"
    echo "  sudo systemctl enable docker"
    echo ""
    exit 1
fi
echo "✓ Docker is installed"

# Check if Docker daemon is running
if ! docker info &> /dev/null; then
    echo "ERROR: Docker daemon is not running or user lacks permissions."
    echo ""

    # Check if user is in docker group
    if ! groups | grep -q docker; then
        echo "Adding user to docker group..."
        sudo usermod -aG docker $USER
        echo ""
        echo "✓ User added to docker group"
        echo ""
        echo "ACTION REQUIRED:"
        echo "  Log out and log back in for group changes to take effect."
        echo "  Then run this script again."
        echo ""
        exit 0
    else
        echo "User is in docker group but Docker is not accessible."
        echo "Try starting Docker:"
        echo "  sudo systemctl start docker"
        echo ""
        exit 1
    fi
fi
echo "✓ Docker daemon is running"

# Check Docker permissions
if ! docker ps &> /dev/null; then
    echo "ERROR: Cannot access Docker."
    echo "Current user: $USER"
    echo "Docker groups: $(groups | grep -o docker || echo 'NONE')"
    echo ""
    echo "If you just added yourself to docker group:"
    echo "  Log out and log back in"
    echo ""
    echo "Otherwise, run:"
    echo "  sudo usermod -aG docker $USER"
    echo "  newgrp docker"
    echo ""
    exit 1
fi
echo "✓ Docker is accessible"

# Configure Testcontainers
echo ""
echo "[2/4] Configuring Testcontainers..."
if ! grep -q "testcontainers.reuse.enable=true" ~/.testcontainers.properties 2>/dev/null; then
    echo "testcontainers.reuse.enable=true" >> ~/.testcontainers.properties
    echo "✓ Enabled Testcontainers container reuse"
else
    echo "✓ Testcontainers already configured"
fi

# Install Playwright Chromium browser with system dependencies
echo ""
echo "[3/4] Installing Playwright Chromium browser..."
./mvnw exec:java \
    -Dexec.mainClass="com.microsoft.playwright.CLI" \
    -Dexec.classpathScope=test \
    -Dexec.args="install --with-deps chromium"

echo ""
echo "[4/4] Verifying setup..."
echo "✓ Docker: $(docker --version)"
echo "✓ User: $USER (groups: $(groups | tr ' ' ','))"
echo "✓ Testcontainers: reuse enabled"
echo "✓ Playwright browsers: ~/.cache/ms-playwright"

echo ""
echo "=== Setup Complete ==="
echo ""
echo "You can now run tests:"
echo "  ./mvnw test                              # All tests"
echo "  ./mvnw test -Dtest=MfgBomTest           # Specific test"
echo "  ./mvnw test -Dplaywright.headless=false # Visible browser"
echo ""
