class InputException(Exception):
    def __init__(self, message=None, cause=None):
        super().__init__(message)
        self.cause = cause

class ProcessingException(Exception):
    def __init__(self, message=None, cause=None):
        super().__init__(message)
        self.cause = cause

# Example usage:
if __name__ == "__main__":
    try:
        raise InputException("This is an input exception")
    except InputException as e:
        print(f"Caught an exception: {e}")
    try:
        raise ProcessingException("This is a processing exception")
    except ProcessingException as e:
        print(f"Caught an exception: {e}")
