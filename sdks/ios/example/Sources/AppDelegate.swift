import UIKit

/// Plain UIKit entry point. No storyboard, no scene manifest, no React Native.
@main
final class AppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        let window = UIWindow(frame: UIScreen.main.bounds)
        window.rootViewController = CollectionViewController()
        window.makeKeyAndVisible()
        self.window = window
        return true
    }
}
