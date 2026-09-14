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
        let collect: () -> NSDictionary
    }

    private let applicationInfo = ApplicationInfoProvider()
    private let audioLatency = AudioLatencyProvider()
    private let locale = LocaleInfoProvider()
    private let network = NetworkInfoProvider()
    private let numeric = NumericConsistencyProvider()
    private let runtimeTiming = RuntimeTimingProvider()
    private let telephony = TelephonyInfoProvider()

    private let output = UITextView()

    private var collections: [Collection] {
        [
            Collection(title: "Runtime timing") { self.runtimeTiming.runtimeTimingSignals() as NSDictionary },
            Collection(title: "Numeric consistency") { self.numeric.numericConsistencySignals() as NSDictionary },
            Collection(title: "Locale") { self.locale.localeSignals() as NSDictionary },
            Collection(title: "Application") { self.applicationInfo.applicationSignals() as NSDictionary },
            Collection(title: "Telephony") { self.telephony.telephonySignals() as NSDictionary },
            Collection(title: "Audio latency (ships disabled)") { self.audioLatency.audioLatency() as NSDictionary },
            Collection(title: "Network (local observations)") { self.network.networkSignals() as NSDictionary },
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
            Device identity is not available from the iOS package yet.
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

        let column = UIStackView(arrangedSubviews: [header, buttons])
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
        let raw = collection.collect()
        output.text = "\(collection.title)\n\n\(Self.readableJSON(raw))"
        output.setContentOffset(.zero, animated: false)
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
