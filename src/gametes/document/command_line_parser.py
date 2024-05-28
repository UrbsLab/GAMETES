import sys
import argparse


class CmdLineParserSrc:
    def __init__(self):
        self.parser = argparse.ArgumentParser()
        self.options = {}
        self.values = {}

    def add_option(self, opt):
        if opt.short_form:
            self.parser.add_argument(f'-{opt.short_form}', f'--{opt.long_form}', dest=opt.long_form, required=False, type=opt.type, help=opt.help)
        else:
            self.parser.add_argument(f'--{opt.long_form}', dest=opt.long_form, required=False, type=opt.type, help=opt.help)
        self.options[opt.long_form] = opt
        return opt

    def add_string_option(self, short_form, long_form):
        return self.add_option(Option.StringOption(short_form, long_form))

    def add_integer_option(self, short_form, long_form):
        return self.add_option(Option.IntegerOption(short_form, long_form))

    def add_long_option(self, short_form, long_form):
        return self.add_option(Option.LongOption(short_form, long_form))

    def add_double_option(self, short_form, long_form):
        return self.add_option(Option.DoubleOption(short_form, long_form))

    def add_boolean_option(self, short_form, long_form):
        return self.add_option(Option.BooleanOption(short_form, long_form))

    def get_option_value(self, opt, default=None):
        return self.values.get(opt.long_form, default)

    def parse(self, argv):
        args = self.parser.parse_args(argv)
        self.values = vars(args)
        self.remaining_args = [arg for arg in argv if arg.startswith('-') is False]
    
    def get_remaining_args(self):
        return self.remaining_args


class Option:
    def __init__(self, short_form, long_form, option_type, help_text=""):
        self.short_form = short_form
        self.long_form = long_form
        self.type = option_type
        self.help = help_text

    class StringOption:
        def __init__(self, short_form, long_form):
            Option.__init__(self, short_form, long_form, str)

    class IntegerOption:
        def __init__(self, short_form, long_form):
            Option.__init__(self, short_form, long_form, int)

    class LongOption:
        def __init__(self, short_form, long_form):
            Option.__init__(self, short_form, long_form, int)

    class DoubleOption:
        def __init__(self, short_form, long_form):
            Option.__init__(self, short_form, long_form, float)

    class BooleanOption:
        def __init__(self, short_form, long_form):
            Option.__init__(self, short_form, long_form, 'store_true')


class OptionException(Exception):
    pass


class IllegalOptionValueException(OptionException):
    def __init__(self, opt, value):
        super().__init__(f"Illegal value '{value}' for option {opt.short_form}/{opt.long_form}")
        self.option = opt
        self.value = value


class UnknownOptionException(OptionException):
    def __init__(self, option_name, msg=None):
        if msg is None:
            msg = f"Unknown option '{option_name}'"
        super().__init__(msg)
        self.option_name = option_name


class NotFlagException(UnknownOptionException):
    def __init__(self, option, unflaggish):
        super().__init__(option, f"Illegal option: '{option}', '{unflaggish}' requires a value")
        self.notflag = unflaggish


class UnknownSuboptionException(UnknownOptionException):
    def __init__(self, option, suboption):
        super().__init__(option, f"Illegal option: '{suboption}' in '{option}'")
        self.suboption = suboption

# Example usage
if __name__ == "__main__":
    cmd_parser = CmdLineParserSrc()
    cmd_parser.add_string_option('n', 'name')
    cmd_parser.add_integer_option('a', 'age')
    cmd_parser.add_boolean_option('v', 'verbose')

    try:
        cmd_parser.parse(sys.argv[1:])
        print("Name:", cmd_parser.get_option_value(cmd_parser.options['name']))
        print("Age:", cmd_parser.get_option_value(cmd_parser.options['age']))
        print("Verbose:", cmd_parser.get_option_value(cmd_parser.options['verbose']))
        print("Remaining Args:", cmd_parser.get_remaining_args())
    except OptionException as e:
        print(e)
