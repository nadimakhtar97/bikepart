# TE244 USB label printing

Implemented 2026-11-12. **Physical acceptance pending:** the intended phone and
TE244 were not connected. The user explicitly requested recording this as pending.
Software tests and emulator screenshots are not hardware compatibility evidence.

## Adapter and workflow

The app uses Android USB host APIs directly, without a PC, network, Bluetooth,
Android print service or receipt-printing protocol. USB host support is optional
for installing the app and checked before printing. Exactly one device whose USB
product descriptor identifies TE244 is required; missing/unrecognised descriptors
or ambiguous bulk-OUT endpoints fail closed pending descriptor/firmware review.
No alternate model is silently accepted.

The documented command path is TSC TSPL/TSPL2: SIZE, DIRECTION, REFERENCE, CLS,
BITMAP in overwrite mode, and PRINT 1,1. The exact preview bitmap is rasterized at
8 dots/mm (203 DPI), MSB first, white bits set and black bits clear. Product strings
never become printer commands. A separate complete command stream is submitted
for each copy; short writes, timeouts and disconnects are never retried. Existing
calibrated gap/sensor/ribbon settings are preserved rather than guessing consumables.

References:

- [Android USB host guide](https://developer.android.com/develop/connectivity/usb/host)
- [TSC downloads](https://in.tscprinters.com/en/downloads)
- [TSC TSPL/TSPL2 programming manual download](https://fs.tscprinters.com/en/dl/1/2541)
  (retrieved during implementation; BITMAP example on printed pages 67–68 confirms
  width in bytes, height in dots, overwrite mode and bit polarity. Exact firmware
  command support and physical orientation remain hardware acceptance gates).

Staff preview the selected roll and positive copy count, then separately confirm
the loaded/calibrated profile and copies. Permission approval only enables the next
explicit confirmation; it never sends a pending job. Cancellation and leaving the
screen stop unsent work at copy/chunk boundaries. A USB write already in progress
can run until its bounded timeout. Sent or printer-buffered output cannot be recalled.

Before sending bytes, an atomic device-local record is persisted under Android's
no-backup directory. It holds a description, not replayable commands. Every send,
including apparent USB success, remains unconfirmed until staff inspect physical
output. Reconnect, process restart and recreation do not resend. After interrupted
output, power-cycle the printer to discard partial binary commands, inspect labels,
acknowledge inspection, and enter a fresh remaining-copy count before previewing
and confirming again. Staff can acknowledge and leave when no copies remain.
Only one send/inspection operation can run at a time in the app process.

Printing does not write shop data, revise stock, change identity or enter a cash
checkout transaction. No shop schema migration is needed. Device-local recovery
records are intentionally excluded from transfer/backup. The starting commit for
this ticket has no shop backup/restore implementation; concurrent backup work is
outside this change.

## Software verification

`LabelPrintingTest` exercises interrupted output, cancellation and restart recovery
without replay, and checks literal TSPL bitmap bytes and one-copy commands.
`LabelWorkflowTest` uses the complete app and real Room persistence: permission
denial/grant callbacks do not send, a staff-confirmed transport failure blocks
resending, inspection requires a fresh remaining-copy count, and explicit printing
leaves the entire shop snapshot unchanged. A separate recreation test retains the
device-local inspection record and handles unavailable USB host/printer support.

App/test Kotlin compilation passes. The eight media/appearance/font-size
combinations were captured, with normal and 200% text, and the controls were
visually inspected under `.scratch/usb-visual/`. All 94 theme contrast checks pass.
Actual phone, TalkBack listening and physical output checks remain pending.

The exact staged USB code was built separately under
`.scratch/usb-verification-20261112/` to exclude concurrent backup edits:
compilation, all seven unit tests, and lint pass (zero errors, 16 warnings).
All four `LabelWorkflowTest` methods pass on that snapshot, including the new
permission/failure workflow. Evidence: `.scratch/usb-snapshot-static.log`,
`.scratch/usb-final-workflows.log`, and `.scratch/usb-final-label-results.xml`.

The full instrumentation run completed once: 105 tests, 102 passed and three
UI timeouts (checkout pending edits, search threshold controls and fitment draft
cancellation). Checkout/search timeouts were also recorded by the preceding
label-preview ticket. This is not claimed as a green full-suite run. Full-run
evidence is preserved in `.scratch/usb-full-results.xml` and `usb-full.log`.

All three timed-out methods have passing targeted reruns on the USB snapshot.
Fitment also failed its first isolated rerun, then passed on both the unchanged
starting commit and a repeated USB-snapshot run; its timing cause remains
unconfirmed. No unrelated fitment/checkout/search code or assertions were changed.
Evidence: `.scratch/usb-timeout-reruns.log` (checkout), `usb-search-rerun.log`,
`usb-fitment-rerun.log`, `usb-baseline-fitment.log`, and `usb-fitment-final.log`,
with corresponding result XML files. The final merged checkout, including the
concurrently committed local-backup work, also passes app/test compilation and
all seven unit tests (`.scratch/usb-merged-check.log`).

Independent code reviews against starting commit `4c804348` completed after the
external USB boundary and permission/failure UI coverage were added:

### Standards

No findings in the current working tree or follow-up USB boundary/UI test delta.
The confirmation row exposes a labelled checkbox with a 48dp minimum target.

### Spec

No implementation defects or scope creep. The added UI-triggered failure and
permission callback checks resolve the initial workflow-coverage limitation.
Hardware acceptance is explicitly pending at the user's request.

Review totals: Standards — zero outstanding; Spec — zero implementation findings,
with physical acceptance pending.

## Physical acceptance record — pending

Record tester/date, actual samples and outcomes for each row before accepting USB
integration. Preserve failed samples and return unreadable labels for layout repair.

| Check | Evidence to record | Result |
| --- | --- | --- |
| Intended phone | Model, Android version, USB host/OTG adapter/cable, offline first print | Pending |
| Exact printer | TE244 serial, firmware/self-test, 203 DPI, USB VID/PID/product name/interfaces/endpoints, TSPL support | Pending |
| Permission | Grant and denial with truthful feedback and no implicit send | Pending |
| Calibration | Gap/sensor, offsets, orientation, feed and safe margins on each roll | Pending |
| Package roll | Actual 50 × 30 mm media, material/adhesive/ribbon settings, sample | Pending |
| Shared-bin roll | Actual 70 × 40 mm media, material/adhesive/ribbon settings, sample | Pending |
| Layout | Long title (three lines), full SKU, black-on-white artwork on both rolls | Pending |
| QR | Final real identity, four-module quiet zone, offline phone scanning on both rolls | Pending |
| Reconnect/failure | Disconnect during header/bitmap/print, inspect counts, power-cycle, explicit remaining copies, no duplicate resend | Pending |
| Cancellation | Before send, during transfer, after printer buffering; unsent copies stop, warning is accurate | Pending |
| Restart | Kill/reopen mid-send; inspection record survives and nothing resends | Pending |
| Stock/identity | Print, reprint, fail and cancel leave stock, SKU and QR identity unchanged | Pending |
