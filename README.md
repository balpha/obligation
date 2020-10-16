**This is an old project that was originally hosted on Bitbucket. When they retired support for Mercurial, I moved it to GitHub. I'm not actively working on it.**

---

# Obligation

## Introduction

Obligation is an Android  library that allows you to handle a list of long-running asynchronous methods that depend on each other. You do this by specifying the dependencies in a promises-like pattern, instead of manually building various confusing `AsyncTask`s that initiate each other in the correct order.

This library is very work-in-progress, so please don't currently rely on API stability.

## Using it

Here's an example of a use case that Obligation tries to solve. Imagine you want to display the weather forecast to the user, and for this you need to make several HTTP calls. Some of those calls can only be made when others have finished.

These are the steps that have to be taken:

1. Call a geolocation web API that returns a city identifier.
2. Call a weather API the returns tomorrow's temperature forecast data, given a city identifier.
3. Call an authentication API that lets you know the Id of the currently authenticated user.
4. Call a user preference API that lets you know whether the user wants Celsius or Fahrenheit degrees, given the user id.
5. Display the forecast to the user.

You want to keep the UI responsive while making the web requests, so they have to happen on a different thread. You also want to start each request as soon as you have all the data you need for that request.

Manually implementing this by juggling AsyncTasks can become very tedious. Instead, you can create an `Obligation` class that just specifies the data dependencies and then manages the AsyncTasks for you.

For each piece of data that's being passed around, we need a unique "data ID". This can be any positive integer. For readability, it is strongly recommended not to use numbers directly, but to create a constant for each data ID.

Then you create methods that specify everything you need to do with the data, and you decorate these methods with the four Obligation annotations to specify how things ought to be run. These annotations are:

- `@Goal`: A goal method is a method that you want to be run eventually. Every obligation class should have at least one goal method; otherwise the obligation wouldn't do anything.

- `@Provides`: A provider method is a method that returns one of the pieces of data that we're interested in. This annotation takes a single data ID argument that specifies which piece of data is returned. Each data ID can only be the provided result of a single provider method. This ensures that you don't accidentally use the same data ID for two different pieces of data.

    It is okay to make a provider method return `void`. This is useful if you need a method to be called before some other method, but the method doesn't return anything.
    
- `@Needs`: This annotation specifies which pieces of data this method depends on. It takes one or more data ids (wrap them in curly braces if there are more than one). The method will only be called after all the dependencies are fulfilled, and the pieces of data will be passed as arguments to the method, in the order as specified in the annotation.

    The method parameter types thus have to match the types of the corresponding provider methods.

    If your method should only be called after a certain piece of data is available, but you don't actually need the concrete value (or the corresponding provider is a void method), you can omit that parameter from the method; however, data IDs of this kind have to be specified at the *end* of the annotations's arguments.

- `@Async`: This annotation specifies that this is a long-running method should be run on a background thread. Under the hood, Obligation uses Android's `AsyncTask` for this. Any obligation method not decorated as asynchronous will be run on the UI thread.

With these concepts, our wheather obligation looks like this:

    class WeatherObligation extends Obligation {

        // Data IDs
        static final int THE_CITY = 1;
        static final int THE_TEMPERATURE_UNIT = 2;
        static final int THE_FORECAST = 3;
        static final int THE_USER_ID = 4;

        @Provides(THE_CITY)
        @Async
        private String getCity() {
            MyRequest<String> request = myWebApiClient.getCityByCurrentIPAddress();
            return request.result();
        }

        @Provides(THE_FORECAST)
        @Needs(THE_CITY)
        @Async
        private TemperatureData getForecast(String cityId) {
            MyRequest<TemperatureData> request = myWebApiClient.getTemperatureForecast(cityId);
            return request.result();
        }

        @Provides(THE_USER_ID)
        @Async
        private int getUserId() {
            MyRequest<Integer> request = myWebApiClient.getUserId(myAuthService.getAuthToken());
            return request.result();
        }

        @Provides(THE_TEMPERATURE_UNIT)
        @Needs(THE_USER_ID)
        @Async
        private String getUnit(int userId) {
            MyRequest<String> request = myWebApiClient.getUserTemperatureUnit(userId);
            return request.result();
        }

        @Goal
        @Needs({THE_FORECAST, THE_TEMPERATURE_UNIT})
        private void displayForecast(TemperatureData forecast, String unit) {
            if (unit.equals("CELSIUS"))
                myTextView.setText(forecast.celsius + " °C");
            else if (unit.equals("FAHRENHEIT"))
                myTextView.setText(forecast.fahrenheit + "F");
        }
    }    

All you have to do now is create an instance of this class and call its `fulfill` method:

    myTextView = findViewById(R.id.my_text_view);
    WeatherObligation ob = new WeatherObligation();
    ob.fulfill();
    
## A note on compile-time safety

Obligation uses reflection to figure out the relationship between the various obligation methods. That is obviously something that only happens at runtime. However, Obligation was designed in such a way that almost all errors are theoretically catchable at compile time.

This means that you do not need any sort of preprocessor or other addition to your build toolchain, while still being able to catch errors early. It would, for example, be possible to create a code inspection plugin for your IDE that highlights the errors. Such a plugin doesn't exist yet, but I may at some point cook something up for IntelliJ IDEA.

But even without this, it's easy to be warned of errors early. The `Obligation` class provides two static methods for this:

- `public static String checkObligation(Class<? extends Obligation> cls)`

    This method checks the provided class and returns a string describing the error if it finds one. It returns `null` if there is no error. With that, you could do something like the following:
    
        String error = Obligation.checkObligation(WeatherObligation.class);
        if (error != null)
            throw new RuntimeException("WeatherObligation is broken! " + error);
            
    You can for example call this on app start (maybe even only if `BuildConfig.DEBUG` is true), and you'll immediately know when you've broken something.
    
- `public static String checkAllObligationsInPackage(Context context)`

    This method checks *all* obligation classes in the current package, returning the first error it finds, or null if everything is okay. If you call this on app start (similar to the example above), you don't have to manually list all the `Obligation` subclasses in your app.
    
These are the errors that will be caught:

- Illegal data IDs: The data IDs must be positive integers, and each ID can only be provided by a single provider method. This ensures that you don't accidentally use the same value for two different data ID constants.

- A method that `@Needs` more data than it has parameters, but is neither a provider nor a goal. This is likely a mistake, because such a method would never be run by the obligation mechanism. The only reason for such a situation would be ensuring type safety (see the description of `setData` below), and in this case, the paramter numbers should match exactly.

- A `@Goal` or `@Provides` method that has parameters, but whose `@Needs` annotation specifies too few needed data IDs (or it has no such annotation at all). It wouldn't be possible for this method to be called, because there's no data to be passed into the excess parameters.

- A method that `@Needs` a piece of data that's either not provided at all, or whose type doesn't match the method's corresponding parameter.

- Circular dependencies: If a method's `@Needs` can never be fulfilled because the method provides data that's necessary before one of its dependencies can be fulfilled. The simplest example would be a method that `@Needs(A)` and `@Provides(B)`, and a different method that `@Needs(B)` and `@Provides(A)`.

## Methods

An `Obligation` provides the following methods:

- `public void fulfill()`

    This is the method that does all the magic. Calling it on an obligation will cause all necessary provider methods to be called such that eventually all goal methods are called.
    
    This method must be called from the Android app's UI thread. It can only be called once. You have to create a new instance of the obligation if you want to fulfill it multiple times.

- `protected void setResult(int id, Object data)`

    If you already have some of the necessary data available, e.g. because it's cached somewhere, you can use this method to provide the data. The corresponding provider method then doesn't have to be run. This method is protected; you should create a concrete public method for setting particular kinds of data. Because calls to `setData` cannot be checked for type errors early, enforcing this method be only called from within the subclass reduces the error surface significantly. You can decorate your setter method with `@Needs` to ensure the type safety:
    
        @Needs(THE_CITY)
        public void setCity(String cityId) {
            setData(THE_CITY, cityId);
        }
    
    Should you ever change the type of the city ID to something other than a string, but forget to change the `setCity` signature, this error will be caught early. If `setResult` were allowed to be called from the outside, this would not be possible.
    
    Note that the `setCity` example method will never be called by the obligation mechanism, because it is neither a goal nor a provider. The `@Needs` annotation is purely for ensuring that the parameter type matches the type of the `THE_CITY` data.
    
    It is illegal to call `setResult()` after `fulfill()` has been called.
    
- `public void cancel()`

    Cancels the fulfillment, meaning that no new obligation methods will be called (currently running ones will finish however).
    
- `protected void onComplete()`

    This method's base implementation does nothing, but you can override it in your `Obligation` subclass to do something meaningful. It will be called after all goal methods have been run.
    
- `protected void onException(ExceptionWrapper problem, int dataId)`

    This method's base implementation does nothing, but you can override it in your `Obligation` subclass to do something meaningful. It will be called if a provider method throws an exception. See the "Exception handling" section for details.
    
## Exception handling

Obligation provides a central mechanism to deal with exceptions that happen while executing provider methods, to allow you to retry the method later or to provide some sort of default value for the case that a provider method fails. Note that non-provider methods have no special exception handling.

When a provider method throws an exception, two things happen:

1. The fulfillment of the obligation is *suspended*. No more obligation methods will be run until the state of things is cleared up. However already-running methods will continue.

2. The `Obligation` object's `onException` method is called with information about the error. The default implementation of this method does nothing (causing the exception to be rethrown), but you can override it to handle the error.

The `onException` method is always called on the UI thread, even if the exception happened on a background thread.

The `onException` method receives two arguments: An `ExceptionWrapper` instance (described below), and the data ID of the provider that threw the exception.

The `ExceptionWrapper` object has two public fields giving you more information about the error:

- `public final Throwable exception`

    This is the actual exception that was thrown by the provider method.
    
- `public final boolean causedSuspension`

    If this is `true`, the exception caused the fulfillment to be suspended (as describe above under 1). If it is false, then the fulfillment was already suspended when this exception happened (remember that already-running methods will continue, so they might still throw exceptions).
    
With this information, your `onException` implementation has these two choices: It can do nothing; in that case, the exception will be rethrown once `onException` is left. This is the default behavior. Or it can call one of the error handling methods on the `ExceptionWrapper` object (which is appropriately dubbed `problem`) to signal how the fulfillment should continue.

The following error handling methods exist; you can only call one:

- `problem.useResult(Object data)`

    Call this to specify the data to be used. Execution will continue as if the provider method had not thrown, and instead had returned this data.
    
    Be sure to check the `dataId` value passed to `onException` to know *which* provider failed, and thus which type the data should have. Also keep in mind the type safety considerations mentioned in the description of `setResult` in the "Methods" section.
    
- `problem.expectRetry()`  
  `problem.expectRetry(boolean resumeOthers)`

    Call this to specify that you're acknowledging the problem, and that at some later point you will ask the obligation to retry running the provider method.
    
    The parameter `resumeOthers` defaults to `false`. In this case, the fulfillment of the obligation stays suspended until you ask the obligation to retry. If you pass `true` instead, only this particular provider method will stay suspended, but all other obligation methods (that don't depend on this particular data) are allowed to continue.
    
    If you called `expectRetry()`, then at any point later when you think the issue should be fixed, you can call `problem.retry()`, and the provider method will be run once more.
    
    If multiple exceptions happened previously and you called `expectRetry()` on all of them without passing `true` for the `resumeOthers` parameters, then all those problems have to be solved (i.e. `retry()` has to be called on all of them) before execution continues. You can call `retryAll()` instead of `retry()`. This behaves like calling `retry()` on all unsolved problems that block this fulfillment.

    `retry()` and `retryAll()` must be called from the UI thread.
    
    Calling `retry()` on a problem more than once is an error. In particular, if you call `retryAll()` on a problem, you must not call `retry()` on any of the other blocking problems. For this reason, you'll probably only want to call `retryAll()` on the problem that `.causedSuspension`.
    
For example, if multiple providers throw exceptions because the device has no internet connection, then in `onException` you can check `causedSuspension`. If it's true, then this is the first error, and you can show a dialog asking the user to connect to the internet and offering a "try again" button. In the click handler for that button, you can then call `retryAll()`. If on the other hand `causedSuspension` is false, then you just call `expectRetry()` and return, leaving it to the very first problem to later `retryAll()`.