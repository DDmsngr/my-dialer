import 'package:flutter_test/flutter_test.dart';
import 'package:my_dialer/main.dart';

void main() {
  testWidgets('Номеронабиратель показывает введённую цифру', (tester) async {
    await tester.pumpWidget(const MyDialerApp());
    expect(find.text('Телефон'), findsOneWidget);
    await tester.tap(find.text('2').last);
    await tester.pump();
    expect(find.text('2'), findsNWidgets(2));
  });
}
