#import "LocaleInfoProvider.h"

@implementation LocaleInfoProvider

- (NSDictionary *)localeSignals
{
  NSMutableDictionary *result = [NSMutableDictionary dictionary];
  NSLocale *locale = [NSLocale currentLocale];

  [self putString:result key:@"language" value:[locale objectForKey:NSLocaleLanguageCode]];
  [self putString:result key:@"country" value:[locale objectForKey:NSLocaleCountryCode]];
  [self putString:result key:@"currencyCode" value:[locale objectForKey:NSLocaleCurrencyCode]];
  [self putString:result key:@"decimalSeparator" value:[locale objectForKey:NSLocaleDecimalSeparator]];
  [self putString:result key:@"groupingSeparator" value:[locale objectForKey:NSLocaleGroupingSeparator]];

  NSArray<NSString *> *preferred = [NSLocale preferredLanguages];
  if (preferred.count > 0) {
    result[@"languages"] = preferred;
  }

  NSString *measurement = [locale objectForKey:NSLocaleMeasurementSystem];
  if ([measurement isEqualToString:@"Metric"]) {
    result[@"measurementSystem"] = @"metric";
  } else if ([measurement isEqualToString:@"U.S."]) {
    result[@"measurementSystem"] = @"us";
  } else if ([measurement isEqualToString:@"U.K."]) {
    result[@"measurementSystem"] = @"uk";
  }

  NSTimeZone *tz = [NSTimeZone localTimeZone];
  if (tz != nil) {
    [self putString:result key:@"timezoneId" value:tz.name];
    result[@"timezoneOffsetMinutes"] = @(tz.secondsFromGMT / 60);
  }

  NSCalendar *calendar = [NSCalendar currentCalendar];
  [self putString:result key:@"calendar" value:calendar.calendarIdentifier];
  result[@"firstDayOfWeek"] = @((NSInteger)calendar.firstWeekday);

  NSString *timeFormat = [NSDateFormatter dateFormatFromTemplate:@"j" options:0 locale:locale];
  if (timeFormat != nil) {
    result[@"uses24HourClock"] = @([timeFormat rangeOfString:@"a"].location == NSNotFound);
  }

  // keyboardLanguages intentionally omitted on iOS (Required Reason API — see header).
  return result;
}

- (void)putString:(NSMutableDictionary *)dict key:(NSString *)key value:(NSString *_Nullable)value
{
  if (value.length > 0) {
    dict[key] = value;
  }
}

@end
