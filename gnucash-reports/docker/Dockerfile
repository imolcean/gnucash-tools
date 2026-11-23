# GnuCash 5.16 command line, Guile, and headless Firefox: the environment gnucash-reports' tests render in.
# Published as ghcr.io/<owner>/gnucash-headless:<tag> by .github/workflows/gnucash-reports-image.yml;
# the tag is the gnucash.image property in gnucash-reports/pom.xml. Change both together.
#
# Ubuntu 26.10 because its archive carries exactly GnuCash 5.16 (ADR-0007 pins behaviour to that version)
# and, being a released distribution, will not move to 5.17.
FROM ubuntu:26.10

ARG GNUCASH_VERSION=1:5.16-2
ARG FIREFOX_VERSION=153.7.0esr
ARG FIREFOX_SHA256=7d2eb92ca3a7b2a83de092eb9b94c853980214de2fbfdccb2ac39d61c593c5a5

ENV DEBIAN_FRONTEND=noninteractive

# gnucash brings gnucash-cli, Guile 3.0 and GTK (which Firefox needs too); libdbd-pgsql is the Postgres driver
# for libdbi; DejaVu is the one font family, so text measures the same on every machine.
RUN apt-get update \
 && apt-get install -y --no-install-recommends \
        gnucash=${GNUCASH_VERSION} \
        libdbd-pgsql \
        guile-3.0 \
        fonts-dejavu-core \
        tzdata \
        ca-certificates curl xz-utils \
        libdbus-glib-1-2 libasound2t64 libx11-xcb1 \
 && rm -rf /var/lib/apt/lists/*

# Ubuntu's own firefox is a snap, which does not run in a container: take Mozilla's build, pinned and checked.
RUN curl -fsSL -o /tmp/firefox.tar.xz \
        "https://archive.mozilla.org/pub/firefox/releases/${FIREFOX_VERSION}/linux-x86_64/en-US/firefox-${FIREFOX_VERSION}.tar.xz" \
 && echo "${FIREFOX_SHA256}  /tmp/firefox.tar.xz" | sha256sum -c - \
 && tar -xJf /tmp/firefox.tar.xz -C /opt \
 && ln -s /opt/firefox/firefox /usr/local/bin/firefox \
 && rm /tmp/firefox.tar.xz

# Fixed locale and zone: amounts and day boundaries in the reports depend on both.
ENV LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    TZ=Europe/Berlin \
    MOZ_HEADLESS=1

RUN useradd --create-home --uid 10001 gnucash
USER gnucash
WORKDIR /home/gnucash

# The tests start the container idle and run their commands in it.
CMD ["sleep", "infinity"]
