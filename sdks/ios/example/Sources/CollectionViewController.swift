import UIKit

// The package's Objective-C providers are exposed to Swift as a clang module whose name is the
// SwiftPM target name. This single import is the whole integration surface; nothing here reaches
// into the package's implementation files, and nothing here imports React Native.
import IOSDeviceRiskSignals

/// One button per collection the package actually offers today. Nothing is collected on launch,
/// nothing is uploaded, and no score or verdict is derived from what comes back.
final class CollectionViewController: UIViewController {
    private struct Collection {
        let title: String
        var worker: Bool = false
        let collect: () -> NSDictionary
    }

    private let sdk = DeviceRiskSignals()

    private let output = UITextView()

    private var collections: [Collection] {
        [
            Collection(title: "Device identity") { self.sdk.collectDeviceIdentity() as NSDictionary },
            Collection(title: "Hardware") { self.sdk.collectHardware() as NSDictionary },
            Collection(title: "Fonts (sensitive)", worker: true) { self.sdk.collectFonts() as NSDictionary },
            Collection(title: "OS integrity (no sockets)") { self.sdk.collectOsIntegrity() as NSDictionary },
            Collection(title: "Cached location (sensitive)") { self.sdk.collectGeolocation() as NSDictionary },
            Collection(title: "Media / app visibility (sensitive)") { self.sdk.collectMediaBluetoothApps() as NSDictionary },
            Collection(title: "Security posture") { self.sdk.collectDeviceSecurityPosture() as NSDictionary },
            Collection(title: "Transaction snapshot (sensitive)") { self.sdk.collectTransactionSafety() as NSDictionary },
            Collection(title: "Runtime timing", worker: true) { self.sdk.collectRuntimeTiming() as NSDictionary },
            Collection(title: "Numeric consistency", worker: true) { self.sdk.collectNumericConsistency() as NSDictionary },
            Collection(title: "Locale") { self.sdk.collectLocale() as NSDictionary },
            Collection(title: "Application") { self.sdk.collectApplication() as NSDictionary },
            Collection(title: "Telephony") { self.sdk.collectTelephony() as NSDictionary },
            Collection(title: "Audio latency") { self.sdk.collectAudioLatency() as NSDictionary },
            Collection(title: "Network (local observations)") { self.sdk.collectNetwork() as NSDictionary },
            Collection(title: "GPU benchmark (explicit opt-in)", worker: true) { self.sdk.collectGpuBenchmark() as NSDictionary },
        ]
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground

        let header = UILabel()
        header.numberOfLines = 0
        header.font = .preferredFont(forTextStyle: .footnote)
        header.textColor = .secondaryLabel
        header.text = """
            Local SDK example. Choose a collection; results stay on this screen.
            Nothing is collected on launch, nothing is uploaded, and there is no score or verdict.
            Sensitive measurements require an explicit tap. GPU/fonts run on a worker.
            """

        let buttons = UIStackView(arrangedSubviews: collections.enumerated().map { index, collection in
            let button = UIButton(type: .system)
            button.setTitle(collection.title, for: .normal)
            button.accessibilityIdentifier = collection.title
            button.contentHorizontalAlignment = .leading
            button.tag = index
            button.addTarget(self, action: #selector(run(_:)), for: .touchUpInside)
            return button
        })
        buttons.axis = .vertical
        buttons.spacing = 4

        output.isEditable = false
        output.font = .monospacedSystemFont(ofSize: 11, weight: .regular)
        output.text = "No collection has run."
        output.accessibilityIdentifier = "output"

        let scroll = UIScrollView()
        buttons.translatesAutoresizingMaskIntoConstraints = false
        scroll.addSubview(buttons)
        NSLayoutConstraint.activate([
            buttons.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor),
            buttons.bottomAnchor.constraint(equalTo: scroll.contentLayoutGuide.bottomAnchor),
            buttons.leadingAnchor.constraint(equalTo: scroll.contentLayoutGuide.leadingAnchor),
            buttons.trailingAnchor.constraint(equalTo: scroll.contentLayoutGuide.trailingAnchor),
            buttons.widthAnchor.constraint(equalTo: scroll.frameLayoutGuide.widthAnchor),
            scroll.heightAnchor.constraint(equalTo: view.heightAnchor, multiplier: 0.4),
        ])
        let column = UIStackView(arrangedSubviews: [header, scroll])
        column.axis = .vertical
        column.spacing = 12
        column.translatesAutoresizingMaskIntoConstraints = false
        output.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(column)
        view.addSubview(output)

        let guide = view.safeAreaLayoutGuide
        NSLayoutConstraint.activate([
            column.topAnchor.constraint(equalTo: guide.topAnchor, constant: 12),
            column.leadingAnchor.constraint(equalTo: guide.leadingAnchor, constant: 16),
            column.trailingAnchor.constraint(equalTo: guide.trailingAnchor, constant: -16),
            output.topAnchor.constraint(equalTo: column.bottomAnchor, constant: 12),
            output.leadingAnchor.constraint(equalTo: guide.leadingAnchor, constant: 16),
            output.trailingAnchor.constraint(equalTo: guide.trailingAnchor, constant: -16),
            output.bottomAnchor.constraint(equalTo: guide.bottomAnchor, constant: -12),
        ])
    }

    @objc private func run(_ sender: UIButton) {
        let collection = collections[sender.tag]
        sender.isEnabled = false
        let work = {
            let raw = collection.collect()
            let text = "\(collection.title)\n\n\(Self.readableJSON(raw))"
            DispatchQueue.main.async {
                self.output.text = text
                self.output.setContentOffset(.zero, animated: false)
                sender.isEnabled = true
            }
        }
        if collection.worker { DispatchQueue.global(qos: .userInitiated).async(execute: work) }
        else { work() }
    }

    private static func readableJSON(_ raw: NSDictionary) -> String {
        guard JSONSerialization.isValidJSONObject(raw),
              let data = try? JSONSerialization.data(
                  withJSONObject: raw,
                  options: [.prettyPrinted, .sortedKeys]
              ),
              let text = String(data: data, encoding: .utf8)
        else {
            return String(describing: raw)
        }
        return text
    }
}
