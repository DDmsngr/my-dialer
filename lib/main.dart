import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

void main() => runApp(const MyDialerApp());

class MyDialerApp extends StatelessWidget {
  const MyDialerApp({super.key});

  @override
  Widget build(BuildContext context) {
    const ink = Color(0xFF202724);
    return MaterialApp(
      title: 'My Dialer',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        useMaterial3: true,
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFF237A63),
          brightness: Brightness.light,
        ),
        scaffoldBackgroundColor: const Color(0xFFF5F6F3),
        appBarTheme: const AppBarTheme(
          backgroundColor: Color(0xFFF5F6F3),
          foregroundColor: ink,
        ),
      ),
      home: const DialerScreen(),
    );
  }
}

class DialerScreen extends StatefulWidget {
  const DialerScreen({super.key});

  @override
  State<DialerScreen> createState() => _DialerScreenState();
}

class _DialerScreenState extends State<DialerScreen> {
  static const _native = MethodChannel('com.ddmsngr.mydialer/telecom');
  final _number = TextEditingController();
  String _status = 'Звонилка не выбрана приложением по умолчанию';
  bool _defaultDialer = false;
  bool _busy = false;

  @override
  void initState() {
    super.initState();
    _refreshRole();
  }

  Future<void> _refreshRole() async {
    try {
      final held = await _native.invokeMethod<bool>('isDefaultDialer') ?? false;
      if (mounted) {
        setState(() {
          _defaultDialer = held;
          _status = held
              ? 'My Dialer выбрана системной звонилкой'
              : 'Назначьте My Dialer системной звонилкой';
        });
      }
    } on PlatformException catch (error) {
      if (mounted) {
        setState(
          () => _status = error.message ?? 'Не удалось проверить роль звонилки',
        );
      }
    }
  }

  Future<void> _requestRole() async {
    await _native.invokeMethod<void>('requestDefaultDialer');
    await Future<void>.delayed(const Duration(milliseconds: 400));
    await _refreshRole();
  }

  Future<void> _call() async {
    final value = _number.text.trim();
    if (value.isEmpty || _busy) return;
    setState(() {
      _busy = true;
      _status = 'Открываю исходящий вызов';
    });
    try {
      await _native.invokeMethod<void>('requestPermissions');
      await _native.invokeMethod<void>('placeCall', {'number': value});
      if (mounted) setState(() => _status = 'Вызов передан Android Telecom');
    } on PlatformException catch (error) {
      if (error.code == 'MISSING_PERMISSION') {
        if (mounted) {
          setState(
            () =>
                _status = 'Подтвердите разрешения, затем нажмите вызов ещё раз',
          );
        }
      } else if (mounted) {
        setState(() => _status = error.message ?? 'Не удалось начать вызов');
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  void _append(String digit) => setState(() => _number.text += digit);

  @override
  void dispose() {
    _number.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    const digits = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '*', '0', '#'];
    return Scaffold(
      appBar: AppBar(
        title: const Text('Телефон'),
        actions: [
          IconButton(
            tooltip: 'Запросить разрешения',
            onPressed: () => _native.invokeMethod<void>('requestPermissions'),
            icon: const Icon(Icons.security_outlined),
          ),
        ],
      ),
      body: SafeArea(
        child: Column(
          children: [
            if (!_defaultDialer)
              MaterialBanner(
                content: Text(_status),
                leading: const Icon(Icons.phone_android),
                actions: [
                  TextButton(
                    onPressed: _requestRole,
                    child: const Text('Выбрать'),
                  ),
                ],
              ),
            const SizedBox(height: 14),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 24),
              child: TextField(
                controller: _number,
                textAlign: TextAlign.center,
                keyboardType: TextInputType.phone,
                style: const TextStyle(fontSize: 28, color: Color(0xFF202724)),
                decoration: InputDecoration(
                  hintText: 'Введите номер',
                  border: InputBorder.none,
                  suffixIcon: IconButton(
                    tooltip: 'Удалить символ',
                    onPressed: () {
                      final text = _number.text;
                      if (text.isNotEmpty) {
                        setState(
                          () =>
                              _number.text = text.substring(0, text.length - 1),
                        );
                      }
                    },
                    icon: const Icon(Icons.backspace_outlined),
                  ),
                ),
                onChanged: (_) => setState(() {}),
              ),
            ),
            const Divider(indent: 28, endIndent: 28),
            Expanded(
              child: Center(
                child: ConstrainedBox(
                  constraints: const BoxConstraints(
                    maxWidth: 360,
                    maxHeight: 420,
                  ),
                  child: GridView.builder(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 20,
                      vertical: 8,
                    ),
                    physics: const NeverScrollableScrollPhysics(),
                    itemCount: digits.length,
                    gridDelegate:
                        const SliverGridDelegateWithFixedCrossAxisCount(
                          crossAxisCount: 3,
                          childAspectRatio: 1.45,
                          mainAxisSpacing: 6,
                          crossAxisSpacing: 12,
                        ),
                    itemBuilder: (context, index) {
                      final digit = digits[index];
                      return Semantics(
                        button: true,
                        label: 'Клавиша $digit',
                        child: InkWell(
                          borderRadius: BorderRadius.circular(8),
                          onTap: () => _append(digit),
                          child: Center(
                            child: Text(
                              digit,
                              style: const TextStyle(
                                fontSize: 27,
                                fontWeight: FontWeight.w400,
                                color: Color(0xFF202724),
                              ),
                            ),
                          ),
                        ),
                      );
                    },
                  ),
                ),
              ),
            ),
            Padding(
              padding: const EdgeInsets.only(bottom: 12),
              child: IconButton.filled(
                tooltip: 'Позвонить',
                onPressed: _busy || _number.text.isEmpty ? null : _call,
                icon: _busy
                    ? const SizedBox(
                        width: 24,
                        height: 24,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.call),
                style: IconButton.styleFrom(
                  fixedSize: const Size(64, 64),
                  backgroundColor: const Color(0xFF237A63),
                  foregroundColor: Colors.white,
                ),
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 0, 20, 16),
              child: Text(
                _status,
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.bodySmall,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
