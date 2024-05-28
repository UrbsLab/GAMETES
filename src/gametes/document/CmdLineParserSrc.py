import locale
from collections import defaultdict
from abc import ABC, abstractmethod

class CmdLineParserSrc:
    def __init__(self):
        self.remaining_args = []
        self.options = {}
        self.values = defaultdict(list)

    class Option(ABC):
        def __init__(self, short_form=None, long_form=None, wants_value=False):
            if long_form is None:
                raise ValueError("Null long_form not allowed")
            self.short_form = short_form
            self.long_form = long_form
            self.wants_value = wants_value

        def get_value(self, arg, loc):
            if self.wants_value:
                if arg is None:
                    raise CmdLineParserSrc.IllegalOptionValueException(self, "")
                else:
                    return self.parse_value(arg, loc)
            else:
                return True

        @abstractmethod
        def parse_value(self, arg, loc):
            pass

    class BooleanOption(Option):
        def __init__(self, short_form=None, long_form=None):
            super().__init__(short_form, long_form, False)

        def parse_value(self, arg, loc):
            return True

    class DoubleOption(Option):
        def __init__(self, short_form=None, long_form=None):
            super().__init__(short_form, long_form, True)

        def parse_value(self, arg, loc):
            try:
                return float(arg)
            except ValueError:
                raise CmdLineParserSrc.IllegalOptionValueException(self, arg)

    class IntegerOption(Option):
        def __init__(self, short_form=None, long_form=None):
            super().__init__(short_form, long_form, True)

        def parse_value(self, arg, loc):
            try:
                return int(arg)
            except ValueError:
                raise CmdLineParserSrc.IllegalOptionValueException(self, arg)

    class LongOption(Option):
        def __init__(self, short_form=None, long_form=None):
            super().__init__(short_form, long_form, True)

        def parse_value(self, arg, loc):
            try:
                return int(arg)
            except ValueError:
                raise CmdLineParserSrc.IllegalOptionValueException(self, arg)

    class StringOption(Option):
        def __init__(self, short_form=None, long_form=None):
            super().__init__(short_form, long_form, True)

        def parse_value(self, arg, loc):
            return arg

    class OptionException(Exception):
        def __init__(self, msg):
            super().__init__(msg)

    class IllegalOptionValueException(OptionException):
        def __init__(self, option, value):
            msg = f"Illegal value '{value}' for option {('-' + option.short_form + '/') if option.short_form else ''}--{option.long_form}"
            super().__init__(msg)
            self.option = option
            self.value = value

    class NotFlagException(OptionException):
        def __init__(self, option, unflaggish):
            msg = f"Illegal option: '{option}', '{unflaggish}' requires a value"
            super().__init__(msg)
            self.notflag = unflaggish

    class UnknownOptionException(OptionException):
        def __init__(self, option_name, msg=None):
            if msg is None:
                msg = f"Unknown option '{option_name}'"
            super().__init__(msg)
            self.option_name = option_name

    class UnknownSuboptionException(UnknownOptionException):
        def __init__(self, option, suboption):
            msg = f"Illegal option: '{suboption}' in '{option}'"
            super().__init__(option, msg)
            self.suboption = suboption

    def add_option(self, opt):
        if opt.short_form:
            self.options["-" + opt.short_form] = opt
        self.options["--" + opt.long_form] = opt
        return opt

    def add_string_option(self, short_form=None, long_form=None):
        return self.add_option(self.StringOption(short_form, long_form))

    def add_integer_option(self, short_form=None, long_form=None):
        return self.add_option(self.IntegerOption(short_form, long_form))

    def add_long_option(self, short_form=None, long_form=None):
        return self.add_option(self.LongOption(short_form, long_form))

    def add_double_option(self, short_form=None, long_form=None):
        return self.add_option(self.DoubleOption(short_form, long_form))

    def add_boolean_option(self, short_form=None, long_form=None):
        return self.add_option(self.BooleanOption(short_form, long_form))

    def get_option_value(self, opt, default=None):
        values = self.values.get(opt.long_form, [])
        if not values:
            return default
        return values.pop(0)

    def get_option_values(self, opt):
        values = []
        while True:
            value = self.get_option_value(opt, None)
            if value is None:
                return values
            values.append(value)

    def get_remaining_args(self):
        return self.remaining_args

    def parse(self, argv, loc=None):
        if loc is None:
            loc = locale.getdefaultlocale()
        other_args = []
        position = 0
        self.values = defaultdict(list)

        while position < len(argv):
            cur_arg = argv[position]
            if cur_arg.startswith("-"):
                if cur_arg == "--":
                    position += 1
                    break

                value_arg = None
                if cur_arg.startswith("--"):
                    i = cur_arg.find("=")
                    if i != -1:
                        value_arg = cur_arg[i + 1:]
                        cur_arg = cur_arg[:i]
                elif len(cur_arg) > 2:
                    for i in range(1, len(cur_arg)):
                        opt = self.options.get("-" + cur_arg[i])
                        if opt is None:
                            raise CmdLineParserSrc.UnknownSuboptionException(cur_arg, cur_arg[i])
                        if opt.wants_value:
                            raise CmdLineParserSrc.NotFlagException(cur_arg, cur_arg[i])
                        self.add_value(opt, opt.get_value(None, loc))

                    position += 1
                    continue

                opt = self.options.get(cur_arg)
                if opt is None:
                    raise CmdLineParserSrc.UnknownOptionException(cur_arg)

                if opt.wants_value:
                    if value_arg is None:
                        position += 1
                        if position < len(argv):
                            value_arg = argv[position]
                value = opt.get_value(value_arg, loc)
                self.add_value(opt, value)
                position += 1
            else:
                other_args.append(cur_arg)
                position += 1

        while position < len(argv):
            other_args.append(argv[position])
            position += 1

        self.remaining_args = other_args

    def add_value(self, opt, value):
        self.values[opt.long_form].append(value)
